package com.sunny.gateway.routing;

import com.sunny.gateway.provider.LLMProvider;

/** Internal request metadata; never serialized into the completion response. */
public record RoutingDecision(LLMProvider provider, String upstreamModelId,
                              RoutingProperties.Strategy strategy, String correlationId) {
    @Override public String toString() {
        return "RoutingDecision[provider=" + provider.id() + ", upstreamModelId=" + upstreamModelId
                + ", strategy=" + strategy + ", correlationId=" + correlationId + "]";
    }
}
