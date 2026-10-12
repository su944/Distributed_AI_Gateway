package com.sunny.gateway.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class ApplicationConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer());

    @Test
    void defaultsExposeOnlyHealthWithoutErrorDetails() {
        contextRunner.run(context -> {
            var environment = context.getEnvironment();
            assertThat(environment.getProperty("management.endpoints.web.exposure.include")).isEqualTo("health");
            assertThat(environment.getProperty("management.endpoint.health.show-details")).isEqualTo("never");
            assertThat(environment.getProperty("server.error.include-stacktrace")).isEqualTo("never");
            assertThat(environment.getProperty("server.error.include-message")).isEqualTo("never");
            assertThat(environment.getProperty("server.address")).isEqualTo("127.0.0.1");
        });
    }

    @Test
    void productionProfileUsesStructuredLogsAndAcceptsExternalSettings() {
        contextRunner.withPropertyValues("spring.profiles.active=prod", "SERVER_PORT=9090", "LOG_LEVEL=WARN")
                .run(context -> {
                    var environment = context.getEnvironment();
                    assertThat(environment.getProperty("management.endpoints.web.exposure.include")).isEqualTo("health");
                    assertThat(environment.getProperty("logging.structured.format.console")).isEqualTo("ecs");
                    assertThat(environment.getProperty("server.port")).isEqualTo("9090");
                    assertThat(environment.getProperty("logging.level.root")).isEqualTo("WARN");
                });
    }
}
