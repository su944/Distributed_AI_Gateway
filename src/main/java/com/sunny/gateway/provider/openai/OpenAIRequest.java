package com.sunny.gateway.provider.openai;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
record OpenAIRequest(String model, List<Message> messages, BigDecimal temperature,
        @JsonProperty("max_completion_tokens") Integer maxCompletionTokens, boolean stream, int n) {
    record Message(String role, String content) {}
}
