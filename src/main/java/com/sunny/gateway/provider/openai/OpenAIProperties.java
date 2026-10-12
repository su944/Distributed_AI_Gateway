package com.sunny.gateway.provider.openai;

import jakarta.validation.constraints.*;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
import java.time.Duration;
import java.util.Map;

@Validated
@ConfigurationProperties("gateway.openai")
public record OpenAIProperties(boolean enabled,
        @NotEmpty Map<@NotBlank String, @NotBlank String> models,
        @NotNull Duration connectTimeout,
        @NotNull Duration responseTimeout,
        @NotNull Duration requestTimeout) {
    public OpenAIProperties {
        if (models != null) models = Map.copyOf(models);
    }
    @AssertTrue(message = "Timeouts must be positive milliseconds and connect timeout must fit an integer")
    public boolean isValidTimeouts() {
        return valid(connectTimeout) && valid(responseTimeout) && valid(requestTimeout)
                && connectTimeout.toMillis() <= Integer.MAX_VALUE;
    }
    private boolean valid(Duration duration) {
        return duration != null && !duration.isNegative() && duration.compareTo(Duration.ofMillis(1)) >= 0
                && duration.compareTo(Duration.ofDays(1)) <= 0;
    }
    @AssertTrue(message = "OpenAI model aliases must not reserve mock-chat")
    public boolean isValidAliases() { return models != null && !models.containsKey("mock-chat"); }
}
