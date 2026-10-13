package com.sunny.gateway.routing;

/** Supplies a uniform integer in [0, exclusiveBound). */
@FunctionalInterface
public interface RandomSource {
    long nextLong(long exclusiveBound);
}
