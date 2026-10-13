package com.sunny.gateway.authentication;

import java.util.Set;
import java.util.UUID;

public record GatewayPrincipal(UUID tenantId, UUID keyId, Set<Permission> permissions) {
    public GatewayPrincipal { permissions = Set.copyOf(permissions); }
}
