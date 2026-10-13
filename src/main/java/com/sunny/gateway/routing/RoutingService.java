package com.sunny.gateway.routing;

import com.sunny.gateway.authentication.GatewayPrincipal;
import com.sunny.gateway.authentication.Permission;
import com.sunny.gateway.gateway.GatewayException;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import java.util.*;
import static com.sunny.gateway.gateway.GatewayException.Reason.*;

@Service
public class RoutingService {
    public static final String DECISION_ATTRIBUTE = RoutingService.class.getName() + ".decision";
    private static final Logger log = LoggerFactory.getLogger(RoutingService.class);
    private final ModelRegistry registry;
    private final Map<RoutingProperties.Strategy, RoutingStrategy> strategies;
    private final MeterRegistry metrics;
    public RoutingService(ModelRegistry registry, List<RoutingStrategy> strategies, MeterRegistry metrics) {
        this.registry = registry;
        this.metrics = metrics;
        var byType = new EnumMap<RoutingProperties.Strategy, RoutingStrategy>(RoutingProperties.Strategy.class);
        for (var strategy : strategies) {
            if (byType.putIfAbsent(strategy.type(), strategy) != null) throw new IllegalArgumentException("Duplicate routing strategy");
        }
        if (byType.size() != RoutingProperties.Strategy.values().length) throw new IllegalArgumentException("Missing routing strategy");
        this.strategies = Map.copyOf(byType);
    }
    public RoutingDecision route(String alias) {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof GatewayPrincipal principal)
                || !principal.permissions().contains(Permission.CHAT_COMPLETIONS)) {
            throw new GatewayException(MODEL_FORBIDDEN);
        }
        var model = registry.model(alias);
        if (model == null) throw new GatewayException(UNSUPPORTED_MODEL);
        var policy = registry.policy(principal.tenantId());
        if (!policy.allowedModels().contains(alias)) throw new GatewayException(MODEL_FORBIDDEN);
        var authorized = model.candidates().stream().filter(c -> policy.allowedProviders().contains(c.providerId())).toList();
        if (authorized.isEmpty()) throw new GatewayException(MODEL_FORBIDDEN);
        var eligible = authorized.stream().filter(c -> model.enabled() && c.enabled() && registry.provider(c.providerId()) != null).toList();
        if (eligible.isEmpty()) throw new GatewayException(PROVIDER_UNAVAILABLE);
        var candidate = strategies.get(model.strategy()).select(eligible);
        String correlation = MDC.get("correlation_id");
        if (correlation == null) correlation = UUID.randomUUID().toString();
        var decision = new RoutingDecision(registry.provider(candidate.providerId()), candidate.upstreamModelId(), model.strategy(), correlation);
        metrics.counter("gateway.routing.decisions", "provider", candidate.providerId(), "strategy", model.strategy().name()).increment();
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            attributes.getRequest().setAttribute(DECISION_ATTRIBUTE, decision);
        }
        log.atInfo().addKeyValue("correlation_id", correlation).addKeyValue("provider", candidate.providerId())
                .addKeyValue("upstream_model", candidate.upstreamModelId()).addKeyValue("strategy", model.strategy().name())
                .log("Routing decision");
        return decision;
    }
}
