package com.sunny.gateway.routing;

import com.fasterxml.jackson.databind.JsonNode;
import com.sunny.gateway.authentication.*;
import com.sunny.gateway.gateway.*;
import com.sunny.gateway.provider.*;
import com.sunny.gateway.support.PostgresIntegrationSupport;
import com.sunny.gateway.tenant.TenantService;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.*;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.*;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "gateway.routing.models.priority-chat.candidates[0].provider-id=mock",
        "gateway.routing.models.priority-chat.candidates[0].upstream-model-id=mock-chat",
        "gateway.routing.models.priority-chat.candidates[0].priority=0",
        "gateway.routing.models.priority-chat.candidates[1].provider-id=test-provider",
        "gateway.routing.models.priority-chat.candidates[1].upstream-model-id=test-a",
        "gateway.routing.models.priority-chat.candidates[1].priority=0",
        "gateway.routing.models.priority-chat.candidates[1].enabled=false",
        "gateway.routing.models.priority-chat.candidates[2].provider-id=test-provider",
        "gateway.routing.models.priority-chat.candidates[2].upstream-model-id=test-b",
        "gateway.routing.models.priority-chat.candidates[2].priority=5",
        "gateway.routing.models.balanced.strategy=WEIGHTED_RANDOM",
        "gateway.routing.models.balanced.candidates[0].provider-id=mock",
        "gateway.routing.models.balanced.candidates[0].upstream-model-id=mock-chat",
        "gateway.routing.models.balanced.candidates[0].weight=1",
        "gateway.routing.models.balanced.candidates[1].provider-id=test-provider",
        "gateway.routing.models.balanced.candidates[1].upstream-model-id=test-a",
        "gateway.routing.models.balanced.candidates[1].weight=3",
        "gateway.routing.models.disabled-chat.enabled=false",
        "gateway.routing.models.disabled-chat.candidates[0].provider-id=mock",
        "gateway.routing.models.disabled-chat.candidates[0].upstream-model-id=mock-chat",
        "gateway.routing.models.unavailable-chat.candidates[0].provider-id=openai",
        "gateway.routing.models.unavailable-chat.candidates[0].upstream-model-id=gpt-4o-mini",
        "gateway.routing.default-policy.allowed-models[0]=mock-chat",
        "gateway.routing.default-policy.allowed-models[1]=priority-chat",
        "gateway.routing.default-policy.allowed-models[2]=balanced",
        "gateway.routing.default-policy.allowed-models[3]=disabled-chat",
        "gateway.routing.default-policy.allowed-models[4]=unavailable-chat",
        "gateway.routing.default-policy.allowed-providers[0]=mock",
        "gateway.routing.default-policy.allowed-providers[1]=test-provider",
        "gateway.routing.default-policy.allowed-providers[2]=openai"})
@ActiveProfiles("test")
@Import(RoutingIT.TestAdapters.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@org.junit.jupiter.api.extension.ExtendWith(OutputCaptureExtension.class)
class RoutingIT extends PostgresIntegrationSupport {
    static final UUID RESTRICTED = UUID.randomUUID();
    static final UUID DENIED = UUID.randomUUID();
    @DynamicPropertySource static void tenantPolicies(DynamicPropertyRegistry registry) {
        registry.add("gateway.routing.tenants." + RESTRICTED + ".allowed-models[0]", () -> "priority-chat");
        registry.add("gateway.routing.tenants." + RESTRICTED + ".allowed-providers[0]", () -> "test-provider");
        registry.add("gateway.routing.tenants." + DENIED + ".allowed-models[0]", () -> "priority-chat");
        registry.add("gateway.routing.tenants." + DENIED + ".allowed-providers[0]", () -> "openai");
    }
    @Autowired TestRestTemplate client;
    @Autowired TenantService tenants;
    @Autowired ApiKeyService keys;
    @Autowired JdbcTemplate jdbc;
    @Autowired TestAdapter adapter;
    @Autowired MeterRegistry metrics;
    String key;
    String restrictedKey;
    String deniedKey;
    @BeforeAll void restrictedTenants() {
        restrictedKey = fixedTenant(RESTRICTED);
        deniedKey = fixedTenant(DENIED);
    }
    String fixedTenant(UUID id) {
        String slug = "route-fixed-" + id;
        var created = tenants.create(slug, slug);
        jdbc.update("update tenants set id = ? where id = ?", id, created.id());
        return keys.issue(id, "test", Set.of(Permission.CHAT_COMPLETIONS), null).plaintext();
    }
    @BeforeEach void provision() {
        String slug = "route-" + UUID.randomUUID();
        key = keys.issue(tenants.create(slug, slug).id(), "test", Set.of(Permission.CHAT_COMPLETIONS), null).plaintext();
        adapter.calls.set(0);
    }
    ResponseEntity<JsonNode> chat(String bearer, String alias) {
        var headers = new HttpHeaders(); headers.setContentType(MediaType.APPLICATION_JSON); headers.setBearerAuth(bearer);
        headers.set("X-Tenant-ID", RESTRICTED.toString()); headers.set("X-Request-ID", "untrusted-client-id");
        return client.postForEntity("/v1/chat/completions", new HttpEntity<>(
                "{\"model\":\"" + alias + "\",\"messages\":[{\"role\":\"user\",\"content\":\"secret-test-prompt\"}]}", headers), JsonNode.class);
    }
    @Test void priorityRoutesToMockAndIgnoresClientTenantId() {
        var response = chat(key, "priority-chat");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().path("model").asText()).isEqualTo("priority-chat");
        assertThat(response.getBody().path("id").asText()).startsWith("mock-");
        assertThat(adapter.calls.get()).isZero();
    }
    @Test void weightedRouteInvokesSelectedUpstreamOnceAndPreservesPublicAlias(CapturedOutput output) {
        var response = chat(key, "balanced");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().path("model").asText()).isEqualTo("balanced");
        assertThat(adapter.selectedModel).isEqualTo("test-a");
        assertThat(adapter.calls.get()).isEqualTo(1);
        assertThat(UUID.fromString(response.getHeaders().getFirst("X-Request-ID"))).isNotNull();
        assertThat(response.getBody().toString()).doesNotContain("test-provider", "test-a");
        assertThat(output.getAll()).contains("Routing decision").doesNotContain(key, "secret-test-prompt", "untrusted-client-id");
        assertThat(metrics.get("gateway.routing.decisions").tags("provider", "test-provider", "strategy", "WEIGHTED_RANDOM").counter().count()).isGreaterThanOrEqualTo(1);
    }
    @Test void tenantProviderRestrictionFiltersHigherPriorityCandidate() {
        var response = chat(restrictedKey, "priority-chat");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(adapter.selectedModel).isEqualTo("test-b");
        assertThat(adapter.calls.get()).isEqualTo(1);
    }
    @Test void tenantModelAndProviderDenialsReturn403WithoutInvocation() {
        for (var response : List.of(chat(restrictedKey, "mock-chat"), chat(deniedKey, "priority-chat"))) {
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(response.getBody().path("code").asText()).isEqualTo("model_forbidden");
        }
        assertThat(adapter.calls.get()).isZero();
    }
    @Test void unknownDisabledAndUnavailableModelsReturnSanitizedErrors() {
        var unknown = chat(key, "unknown");
        assertThat(unknown.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(unknown.getBody().path("code").asText()).isEqualTo("unsupported_model");
        for (String alias : List.of("disabled-chat", "unavailable-chat")) {
            var response = chat(key, alias);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
            assertThat(response.getBody().path("code").asText()).isEqualTo("provider_unavailable");
            assertThat(response.getBody().toString()).doesNotContain("secret-test-prompt", "gpt-4o-mini");
        }
        assertThat(adapter.calls.get()).isZero();
    }
    @TestConfiguration(proxyBeanMethods = false)
    static class TestAdapters {
        @Bean TestAdapter routingTestProvider() { return new TestAdapter(); }
        @Bean @Primary RandomSource deterministicRoutingRandom() { return bound -> bound - 1; }
    }
    static class TestAdapter implements LLMProvider {
        AtomicInteger calls = new AtomicInteger();
        volatile String selectedModel;
        public String id() { return "test-provider"; }
        public boolean supportsUpstream(String model) { return Set.of("test-a", "test-b").contains(model); }
        public boolean supports(String model) { return false; }
        public ChatCompletionResponse complete(ChatCompletionRequest request) { throw new AssertionError("Legacy invocation used"); }
        public ChatCompletionResponse complete(ChatCompletionRequest request, String model) {
            calls.incrementAndGet(); selectedModel = model;
            return new ChatCompletionResponse("test-completion", "vendor-version", List.of(new ChatCompletionResponse.Choice(
                    0, new ChatMessage("assistant", "Test reply"), "stop")), new ChatCompletionResponse.Usage(1, 1, 2));
        }
    }
}
