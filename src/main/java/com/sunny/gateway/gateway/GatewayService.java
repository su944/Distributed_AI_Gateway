package com.sunny.gateway.gateway;

import com.sunny.gateway.routing.ProviderSelector;
import org.springframework.stereotype.Service;

@Service
public class GatewayService {
    private final ProviderSelector selector;
    public GatewayService(ProviderSelector selector) { this.selector = selector; }
    public ChatCompletionResponse complete(ChatCompletionRequest request) {
        return selector.select(request.model()).complete(request);
    }
}
