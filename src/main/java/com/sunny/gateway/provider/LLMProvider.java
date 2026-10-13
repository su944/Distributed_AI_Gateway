package com.sunny.gateway.provider;

import com.sunny.gateway.gateway.ChatCompletionRequest;
import com.sunny.gateway.gateway.ChatCompletionResponse;

public interface LLMProvider {
    String id();
    boolean supportsUpstream(String upstreamModel);
    ChatCompletionResponse complete(ChatCompletionRequest request, String upstreamModel);

    // Legacy adapter entry points remain available; gateway routing uses the explicit upstream model.
    default boolean supports(String model) { return supportsUpstream(model); }
    default ChatCompletionResponse complete(ChatCompletionRequest request) { return complete(request, request.model()); }
}
