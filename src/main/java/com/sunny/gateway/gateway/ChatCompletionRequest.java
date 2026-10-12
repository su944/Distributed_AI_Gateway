package com.sunny.gateway.gateway;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.List;

public record ChatCompletionRequest(
        @NotBlank @Size(max = 128) String model,
        @NotEmpty @Size(max = 100) List<@NotNull @Valid ChatMessage> messages,
        @DecimalMin("0.0") @DecimalMax("2.0") BigDecimal temperature,
        @JsonProperty("max_tokens") @Min(1) @Max(4096) Integer maxTokens,
        @AssertFalse Boolean stream) {}
