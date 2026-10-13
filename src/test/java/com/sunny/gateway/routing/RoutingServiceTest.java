package com.sunny.gateway.routing;

import com.sunny.gateway.authentication.*;
import com.sunny.gateway.gateway.*;
import com.sunny.gateway.provider.*;
import com.sunny.gateway.provider.openai.OpenAIProperties;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.*;
import java.time.Duration;
import java.util.*;
import static com.sunny.gateway.routing.RoutingProperties.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class RoutingServiceTest {
    UUID tenant = UUID.randomUUID();
    SimpleMeterRegistry metrics = new SimpleMeterRegistry();
    @BeforeEach void authenticate() {
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                new GatewayPrincipal(tenant, UUID.randomUUID(), Set.of(Permission.CHAT_COMPLETIONS)), null, List.of()));
    }
    @AfterEach void cleanup() { SecurityContextHolder.clearContext(); RequestContextHolder.resetRequestAttributes(); metrics.close(); }
    static OpenAIProperties openAI(boolean enabled) {
        return new OpenAIProperties(enabled, Map.of("openai-chat", "gpt-4o-mini"), Duration.ofSeconds(1), Duration.ofSeconds(2), Duration.ofSeconds(3));
    }
    RoutingService service(Map<String, Model> models, TenantPolicy policy, Map<UUID, TenantPolicy> tenants, LLMProvider... providers) {
        return new RoutingService(new ModelRegistry(new RoutingProperties(models, policy, tenants), openAI(false), List.of(providers)),
                List.of(new PriorityRoutingStrategy(), new WeightedRandomRoutingStrategy(bound -> bound - 1)), metrics);
    }
    Candidate candidate(String provider, String model, int priority, boolean enabled) { return new Candidate(provider, model, priority, 1, enabled); }
    Model model(Strategy strategy, Candidate... candidates) { return new Model(strategy, true, List.of(candidates)); }
    TenantPolicy policy(String alias, String... providers) { return new TenantPolicy(Set.of(alias), Set.of(providers)); }
    @Test void defaultPolicyPreservesMockButDoesNotGrantNewAliases() {
        var routing = service(Map.of("new-chat", model(Strategy.PRIORITY, candidate("mock", "mock-chat", 0, true))), null, null, new MockLLMProvider());
        assertThat(routing.route("mock-chat").provider().id()).isEqualTo("mock");
        reason(() -> routing.route("new-chat"), GatewayException.Reason.MODEL_FORBIDDEN);
    }
    @Test void excludesDisabledCandidatesAndUnavailableProvidersForBothStrategies() {
        for (var strategy : Strategy.values()) {
            var routing = service(Map.of("chat", model(strategy, candidate("mock", "mock-chat", 0, true),
                    candidate("openai", "gpt-4o-mini", 0, true), candidate("openai", "other-model", 0, false))),
                    policy("chat", "mock", "openai"), null, new MockLLMProvider());
            assertThat(routing.route("chat").provider().id()).isEqualTo("mock");
        }
    }
    @Test void modelAndProviderPolicyRestrictionsAreEnforced() {
        var model = model(Strategy.PRIORITY, candidate("mock", "mock-chat", 0, true));
        var routing = service(Map.of("chat", model), policy("chat", "mock"), Map.of(tenant, policy("mock-chat", "mock")), new MockLLMProvider());
        reason(() -> routing.route("chat"), GatewayException.Reason.MODEL_FORBIDDEN);
        var noProvider = service(Map.of("chat", model), policy("chat", "openai"), null, new MockLLMProvider());
        reason(() -> noProvider.route("chat"), GatewayException.Reason.MODEL_FORBIDDEN);
    }
    @Test void unknownAndUnavailableAndDisabledModelsHaveDistinctErrors() {
        var routing = service(Map.of("chat", model(Strategy.PRIORITY, candidate("openai", "gpt-4o-mini", 0, true))),
                policy("chat", "openai"), null, new MockLLMProvider());
        reason(() -> routing.route("unknown"), GatewayException.Reason.UNSUPPORTED_MODEL);
        reason(() -> routing.route("chat"), GatewayException.Reason.PROVIDER_UNAVAILABLE);
        var disabled = service(Map.of("chat", new Model(Strategy.PRIORITY, false, List.of(candidate("mock", "mock-chat", 0, true)))),
                policy("chat", "mock"), null, new MockLLMProvider());
        reason(() -> disabled.route("chat"), GatewayException.Reason.PROVIDER_UNAVAILABLE);
    }
    @Test void principalIsRequiredEvenWhenServiceIsCalledDirectly() {
        var routing = service(null, null, null, new MockLLMProvider());
        SecurityContextHolder.clearContext();
        reason(() -> routing.route("mock-chat"), GatewayException.Reason.MODEL_FORBIDDEN);
    }
    @Test void storesInternalDecisionAndMetricsHaveOnlyProviderAndStrategyTags() {
        var request = new MockHttpServletRequest(); RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        var routing = service(null, null, null, new MockLLMProvider());
        var decision = routing.route("mock-chat");
        assertThat(request.getAttribute(RoutingService.DECISION_ATTRIBUTE)).isSameAs(decision);
        assertThat(decision.upstreamModelId()).isEqualTo("mock-chat");
        assertThat(metrics.get("gateway.routing.decisions").tags("provider", "mock", "strategy", "PRIORITY").counter().count()).isEqualTo(1);
        assertThat(metrics.getMeters().getFirst().getId().getTags()).extracting(io.micrometer.core.instrument.Tag::getKey)
                .containsExactlyInAnyOrder("provider", "strategy");
    }
    @Test void selectedProviderFailureIsNeverRetriedOrSentToAnotherCandidate() {
        var first = mock(LLMProvider.class); var second = mock(LLMProvider.class);
        when(first.id()).thenReturn("first"); when(second.id()).thenReturn("second");
        when(first.supportsUpstream(anyString())).thenReturn(true); when(second.supportsUpstream(anyString())).thenReturn(true);
        when(first.complete(any(), anyString())).thenThrow(new ProviderException(ProviderException.Reason.TIMEOUT));
        var routing = service(Map.of("chat", model(Strategy.PRIORITY, candidate("first", "first-model", 0, true),
                        candidate("second", "second-model", 1, true))), policy("chat", "first", "second"), null, first, second);
        var request = new ChatCompletionRequest("chat", List.of(new ChatMessage("user", "Hello")), null, null, null);
        assertThatThrownBy(() -> new GatewayService(routing).complete(request)).isInstanceOf(ProviderException.class);
        verify(first, times(1)).complete(request, "first-model");
        verify(second, never()).complete(any(), anyString());
    }
    private void reason(org.assertj.core.api.ThrowableAssert.ThrowingCallable action, GatewayException.Reason reason) {
        assertThatThrownBy(action).isInstanceOfSatisfying(GatewayException.class, ex -> assertThat(ex.reason()).isEqualTo(reason));
    }
}
