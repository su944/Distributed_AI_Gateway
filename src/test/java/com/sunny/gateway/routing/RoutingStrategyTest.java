package com.sunny.gateway.routing;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Random;
import static org.assertj.core.api.Assertions.*;

class RoutingStrategyTest {
    static RoutingProperties.Candidate candidate(String provider, String model, int priority, long weight) {
        return new RoutingProperties.Candidate(provider, model, priority, weight, true);
    }
    @Test void priorityUsesLowerNumbersThenProviderAndModelForTies() {
        var winner = candidate("a", "a", 1, 1);
        assertThat(new PriorityRoutingStrategy().select(List.of(candidate("z", "a", 1, 1),
                candidate("a", "z", 1, 1), candidate("a", "x", 2, 1), winner))).isEqualTo(winner);
    }
    @Test void weightedSelectionHasDeterministicBoundaries() {
        var first = candidate("a", "a", 0, 1);
        var second = candidate("b", "b", 0, 3);
        assertThat(new WeightedRandomRoutingStrategy(bound -> 0).select(List.of(first, second))).isEqualTo(first);
        assertThat(new WeightedRandomRoutingStrategy(bound -> 1).select(List.of(first, second))).isEqualTo(second);
        assertThat(new WeightedRandomRoutingStrategy(bound -> bound - 1).select(List.of(first, second))).isEqualTo(second);
    }
    @Test void seededDistributionMatchesWeightsWithinTwoPercentagePoints() {
        var first = candidate("a", "a", 0, 1);
        var second = candidate("b", "b", 0, 3);
        var strategy = new WeightedRandomRoutingStrategy(new Random(42)::nextLong);
        int firstCount = 0;
        for (int i = 0; i < 20000; i++) if (strategy.select(List.of(first, second)).equals(first)) firstCount++;
        assertThat(firstCount / 20000.0).isBetween(0.23, 0.27);
    }
}
