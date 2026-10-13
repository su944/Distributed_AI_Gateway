package com.sunny.gateway.routing;

import java.util.List;

/** Selects exactly one candidate from an already authorized, nonempty eligible list. */
public interface RoutingStrategy {
    RoutingProperties.Strategy type();
    RoutingProperties.Candidate select(List<RoutingProperties.Candidate> eligible);
}
