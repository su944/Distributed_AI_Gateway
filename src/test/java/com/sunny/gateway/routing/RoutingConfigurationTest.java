package com.sunny.gateway.routing;

import com.sunny.gateway.provider.*;
import com.sunny.gateway.provider.openai.OpenAIProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static com.sunny.gateway.routing.RoutingProperties.*;

class RoutingConfigurationTest {
    ApplicationContextRunner runner = new ApplicationContextRunner().withUserConfiguration(RoutingConfiguration.class, ModelRegistry.class)
            .withBean(OpenAIProperties.class, () -> RoutingServiceTest.openAI(false))
            .withBean(MockLLMProvider.class, MockLLMProvider::new)
            .withPropertyValues("gateway.routing.models.chat.candidates[0].provider-id=mock",
                    "gateway.routing.models.chat.candidates[0].upstream-model-id=mock-chat");
    @Test void bindsDefaultsAndUuidTenantPolicies() {
        var id = UUID.randomUUID();
        runner.withPropertyValues("gateway.routing.tenants." + id + ".allowed-models[0]=chat",
                "gateway.routing.tenants." + id + ".allowed-providers[0]=mock").run(context -> {
            assertThat(context).hasNotFailed();
            var registry = context.getBean(ModelRegistry.class);
            assertThat(registry.model("chat").strategy()).isEqualTo(Strategy.PRIORITY);
            assertThat(registry.model("chat").candidates().getFirst().weight()).isEqualTo(1);
            assertThat(registry.model("chat").candidates().getFirst().enabled()).isTrue();
            assertThat(registry.policy(id)).isEqualTo(new TenantPolicy(Set.of("chat"), Set.of("mock")));
        });
    }
    @ParameterizedTest
    @ValueSource(strings = {"gateway.routing.models.chat.candidates[0].weight=0",
            "gateway.routing.models.chat.candidates[0].weight=-1", "gateway.routing.models.chat.candidates[0].weight=1000000001",
            "gateway.routing.models.chat.candidates[0].priority=-1", "gateway.routing.models.chat.strategy=TYPO",
            "gateway.routing.models.chat.candidates[0].provider-id=typo", "gateway.routing.models.chat.candidates[0].upstream-model-id=bad-model",
            "gateway.routing.default-policy.allowed-models[0]=missing", "gateway.routing.default-policy.allowed-providers[0]=typo",
            "gateway.routing.models.chat.candidates[0].upstream-model-id="})
    void invalidRoutingConfigurationFailsStartup(String property) { runner.withPropertyValues(property).run(context -> assertThat(context).hasFailed()); }
    @Test void duplicateCandidatesFailStartup() {
        runner.withPropertyValues("gateway.routing.models.chat.candidates[1].provider-id=mock",
                "gateway.routing.models.chat.candidates[1].upstream-model-id=mock-chat")
                .run(context -> assertThat(context).hasFailed());
    }
    @Test void misspelledRoutingPropertiesFailStartup() {
        runner.withPropertyValues("gateway.routing.models.chat.candidates[0].weigth=3")
                .run(context -> assertThat(context).hasFailed());
    }
    @Test void duplicateProviderIdsFailStartup() {
        var duplicate = mock(LLMProvider.class); when(duplicate.id()).thenReturn("mock");
        runner.withBean("duplicate", LLMProvider.class, () -> duplicate).run(context -> assertThat(context).hasFailed());
    }
    @Test void legacyOpenAiAliasesAreImportedAndPreserved() {
        var openAI = RoutingServiceTest.openAI(true);
        var registry = new ModelRegistry(new RoutingProperties(null, null, null), openAI, List.of(new MockLLMProvider()));
        assertThat(registry.model("openai-chat").candidates().getFirst().upstreamModelId()).isEqualTo("gpt-4o-mini");
        assertThat(registry.policy(UUID.randomUUID()).allowedModels()).contains("openai-chat", "mock-chat");
    }
    @Test void emptyModelCandidatesAndUnsafeIdentifiersAreRejected() {
        assertThatThrownBy(() -> new Model(Strategy.PRIORITY, true, List.of())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Candidate("mock", "injected\nmodel", 0, 1, true)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RoutingProperties(Map.of("bad alias", new Model(Strategy.PRIORITY, true,
                List.of(new Candidate("mock", "mock-chat", 0, 1, true)))), null, null)).isInstanceOf(IllegalArgumentException.class);
    }
}
