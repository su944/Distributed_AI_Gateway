package com.sunny.gateway.routing;

import org.springframework.stereotype.Component;
import java.util.Comparator;
import java.util.List;

@Component
public class PriorityRoutingStrategy implements RoutingStrategy {
    @Override public RoutingProperties.Strategy type() { return RoutingProperties.Strategy.PRIORITY; }
    @Override public RoutingProperties.Candidate select(List<RoutingProperties.Candidate> eligible) {
        return eligible.stream().min(Comparator.comparingInt(RoutingProperties.Candidate::priority)
                .thenComparing(RoutingProperties.Candidate::providerId)
                .thenComparing(RoutingProperties.Candidate::upstreamModelId)).orElseThrow();
    }
}
