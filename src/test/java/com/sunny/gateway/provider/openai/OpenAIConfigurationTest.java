package com.sunny.gateway.provider.openai;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import static org.assertj.core.api.Assertions.assertThat;

class OpenAIConfigurationTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withUserConfiguration(OpenAIConfiguration.class)
            .withPropertyValues("OPENAI_API_KEY=", "gateway.openai.enabled=false");

    @Test void disabledByDefaultAndMockDoesNotNeedKey() {
        runner.run(context -> {
            assertThat(context).hasNotFailed().doesNotHaveBean(OpenAIProvider.class);
            assertThat(context.getBean(OpenAIProperties.class).models()).containsEntry("openai-chat", "gpt-4o-mini");
        });
    }
    @Test void enabledProviderBindsCustomModelAndTimeoutsWithoutNeedingKeyAtStartup() {
        runner.withPropertyValues("gateway.openai.enabled=true", "gateway.openai.models.team-chat=custom-model",
                "gateway.openai.connect-timeout=2s", "gateway.openai.response-timeout=3s", "gateway.openai.request-timeout=4s")
                .run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(OpenAIProvider.class);
                    var provider = context.getBean(OpenAIProvider.class);
                    assertThat(provider.supports("team-chat")).isTrue();
                    assertThat(provider.supports("mock-chat")).isFalse();
                    assertThat(context.getBean(OpenAIProperties.class).connectTimeout()).hasSeconds(2);
                });
    }
    @ParameterizedTest
    @ValueSource(strings = {"gateway.openai.connect-timeout=0ms", "gateway.openai.response-timeout=-1s",
            "gateway.openai.request-timeout=0ms", "gateway.openai.models.mock-chat=vendor-model",
            "gateway.openai.models.openai-chat="})
    void invalidConfigurationFailsAtStartup(String property) {
        runner.withPropertyValues(property).run(context -> assertThat(context).hasFailed());
    }
}
