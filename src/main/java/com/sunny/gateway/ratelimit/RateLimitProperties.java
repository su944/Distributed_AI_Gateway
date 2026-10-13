package com.sunny.gateway.ratelimit;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import java.util.Map;
import java.util.UUID;

@ConfigurationProperties("gateway.rate-limit")
public record RateLimitProperties(Policy defaults, OutagePolicy outagePolicy, Map<UUID, Policy> tenants) {
    public RateLimitProperties {
        defaults = defaults == null ? new Policy(60, 1, 1) : defaults;
        outagePolicy = outagePolicy == null ? OutagePolicy.FAIL_CLOSED : outagePolicy;
        tenants = tenants == null ? Map.of() : Map.copyOf(tenants);
    }
    public Policy policy(UUID tenantId) { return tenants.getOrDefault(tenantId, defaults); }
    public enum OutagePolicy { FAIL_CLOSED, FAIL_OPEN }
    public record Policy(@DefaultValue("60") long capacity, @DefaultValue("1") double refillRate,
                         @DefaultValue("1") long requestCost) {
        public Policy {
            if (capacity < 1 || capacity > 1_000_000 || !Double.isFinite(refillRate) || refillRate <= 0
                    || refillRate > 1_000_000 || capacity / refillRate > 86400
                    || requestCost < 1 || requestCost > capacity) {
                throw new IllegalArgumentException("Invalid rate limit policy: positive bounded values and cost <= capacity are required");
            }
        }
    }
}
