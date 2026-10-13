package com.sunny.gateway.routing;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import java.util.*;

@ConfigurationProperties(prefix = "gateway.routing", ignoreUnknownFields = false)
public record RoutingProperties(Map<String, Model> models, TenantPolicy defaultPolicy, Map<UUID, TenantPolicy> tenants) {
    public RoutingProperties {
        models = models == null ? Map.of() : Map.copyOf(models);
        tenants = tenants == null ? Map.of() : Map.copyOf(tenants);
        models.keySet().forEach(RoutingProperties::validateAlias);
    }
    public enum Strategy { PRIORITY, WEIGHTED_RANDOM }
    public record Model(@DefaultValue("PRIORITY") Strategy strategy, @DefaultValue("true") boolean enabled,
                        List<Candidate> candidates) {
        public Model {
            if (strategy == null || candidates == null || candidates.isEmpty() || candidates.size() > 100) {
                throw new IllegalArgumentException("Routing model requires a strategy and 1 to 100 candidates");
            }
            candidates = List.copyOf(candidates);
            var identities = new HashSet<String>();
            for (var candidate : candidates) {
                if (!identities.add(candidate.providerId() + "\n" + candidate.upstreamModelId())) {
                    throw new IllegalArgumentException("Routing model contains duplicate provider/model candidates");
                }
            }
        }
    }
    public record Candidate(String providerId, String upstreamModelId, @DefaultValue("0") int priority,
                            @DefaultValue("1") long weight, @DefaultValue("true") boolean enabled) {
        public Candidate {
            if (providerId == null || !providerId.matches("[a-z][a-z0-9-]{0,63}")
                    || upstreamModelId == null || !upstreamModelId.matches("[A-Za-z0-9][A-Za-z0-9._:/-]{0,255}")
                    || priority < 0 || weight < 1 || weight > 1_000_000_000) {
                throw new IllegalArgumentException("Routing candidate requires valid provider/model IDs, nonnegative priority and weight in 1..1000000000");
            }
        }
    }
    public record TenantPolicy(Set<String> allowedModels, Set<String> allowedProviders) {
        public TenantPolicy {
            allowedModels = allowedModels == null ? Set.of() : Set.copyOf(allowedModels);
            allowedProviders = allowedProviders == null ? Set.of() : Set.copyOf(allowedProviders);
        }
    }
    static void validateAlias(String alias) {
        if (alias == null || !alias.matches("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}")) {
            throw new IllegalArgumentException("Routing alias must contain 1..128 safe model identifier characters");
        }
    }
}
