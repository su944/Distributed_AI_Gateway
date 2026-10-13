package com.sunny.gateway.routing;

import com.sunny.gateway.provider.LLMProvider;
import com.sunny.gateway.provider.openai.OpenAIProperties;
import org.springframework.stereotype.Component;
import java.util.*;
import static com.sunny.gateway.routing.RoutingProperties.*;

/** Immutable operator configuration; unavailable known adapters remain valid configuration. */
@Component
public class ModelRegistry {
    private final Map<String, Model> models;
    private final Map<String, LLMProvider> providers;
    private final Set<String> knownProviders;
    private final TenantPolicy defaultPolicy;
    private final Map<UUID, TenantPolicy> tenantPolicies;
    public ModelRegistry(RoutingProperties properties, OpenAIProperties openAI, List<LLMProvider> adapters) {
        var providerMap = new HashMap<String, LLMProvider>();
        for (var adapter : adapters) {
            String id = adapter.id();
            if (id == null || !id.matches("[a-z][a-z0-9-]{0,63}") || providerMap.putIfAbsent(id, adapter) != null) {
                throw new IllegalArgumentException("Routing adapters must declare unique valid provider IDs");
            }
        }
        providers = Map.copyOf(providerMap);
        var known = new HashSet<>(Set.of("mock", "openai"));
        known.addAll(providers.keySet());
        knownProviders = Set.copyOf(known);
        var registry = new HashMap<String, Model>();
        registry.put("mock-chat", single("mock", "mock-chat"));
        if (openAI.enabled()) openAI.models().forEach((alias, upstream) -> registry.put(alias, single("openai", upstream)));
        var legacyAliases = Set.copyOf(registry.keySet());
        registry.putAll(properties.models());
        registry.forEach((alias, model) -> {
            validateAlias(alias);
            for (var candidate : model.candidates()) {
                if (!knownProviders.contains(candidate.providerId())) {
                    throw new IllegalArgumentException("Routing candidate references an unknown provider ID");
                }
                var adapter = providers.get(candidate.providerId());
                if ((adapter != null && !adapter.supportsUpstream(candidate.upstreamModelId()))
                        || ("mock".equals(candidate.providerId()) && !"mock-chat".equals(candidate.upstreamModelId()))) {
                    throw new IllegalArgumentException("Routing candidate references an unsupported upstream model");
                }
            }
        });
        models = Map.copyOf(registry);
        defaultPolicy = properties.defaultPolicy() == null
                ? new TenantPolicy(legacyAliases, Set.of("mock", "openai")) : properties.defaultPolicy();
        tenantPolicies = properties.tenants();
        validatePolicy(defaultPolicy);
        tenantPolicies.values().forEach(this::validatePolicy);
    }
    private static Model single(String provider, String upstream) {
        return new Model(Strategy.PRIORITY, true, List.of(new Candidate(provider, upstream, 0, 1, true)));
    }
    private void validatePolicy(TenantPolicy policy) {
        if (!models.keySet().containsAll(policy.allowedModels()) || !knownProviders.containsAll(policy.allowedProviders())) {
            throw new IllegalArgumentException("Routing policy references an unknown model alias or provider ID");
        }
    }
    public Model model(String alias) { return models.get(alias); }
    public LLMProvider provider(String id) { return providers.get(id); }
    public TenantPolicy policy(UUID tenantId) { return tenantPolicies.getOrDefault(tenantId, defaultPolicy); }
}
