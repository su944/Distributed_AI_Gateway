package com.sunny.gateway.provider;

import com.sunny.gateway.gateway.ChatCompletionRequest;
import com.sunny.gateway.gateway.ChatCompletionResponse;

public interface LLMProvider {
    boolean supports(String model);
    ChatCompletionResponse complete(ChatCompletionRequest request);
}
