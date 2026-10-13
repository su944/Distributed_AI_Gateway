package com.sunny.gateway.gateway;

import com.sunny.gateway.routing.RoutingService;
import org.springframework.stereotype.Service;

@Service
public class GatewayService {
    private final RoutingService routing;
    public GatewayService(RoutingService routing) { this.routing = routing; }
    public ChatCompletionResponse complete(ChatCompletionRequest request) {
        var decision = routing.route(request.model());
        var response = decision.provider().complete(request, decision.upstreamModelId());
        // Keep the public model equal to the requested gateway alias for every adapter.
        return new ChatCompletionResponse(response.id(), request.model(), response.choices(), response.usage());
    }
}
