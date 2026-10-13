package com.sunny.gateway.routing;

import org.springframework.stereotype.Component;
import java.util.List;

@Component
public class WeightedRandomRoutingStrategy implements RoutingStrategy {
    private final RandomSource random;
    public WeightedRandomRoutingStrategy(RandomSource random) { this.random = random; }
    @Override public RoutingProperties.Strategy type() { return RoutingProperties.Strategy.WEIGHTED_RANDOM; }
    @Override public RoutingProperties.Candidate select(List<RoutingProperties.Candidate> eligible) {
        long total = eligible.stream().mapToLong(RoutingProperties.Candidate::weight).reduce(0, Math::addExact);
        long draw = random.nextLong(total);
        if (draw < 0 || draw >= total) throw new IllegalStateException("Random source returned an invalid routing draw");
        for (var candidate : eligible) {
            if (draw < candidate.weight()) return candidate;
            draw -= candidate.weight();
        }
        throw new IllegalStateException("No eligible routing candidate");
    }
}
