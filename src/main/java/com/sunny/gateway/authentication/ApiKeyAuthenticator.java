package com.sunny.gateway.authentication;

import com.sunny.gateway.tenant.Tenant;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;

@Service
public class ApiKeyAuthenticator {
    private final ApiKeyRepository keys;
    private final ApiKeyCrypto crypto;
    public ApiKeyAuthenticator(ApiKeyRepository keys, ApiKeyCrypto crypto) { this.keys = keys; this.crypto = crypto; }
    @Transactional
    public GatewayPrincipal authenticate(String raw) {
        var parsed = crypto.parse(raw);
        var key = keys.findByPublicId(parsed.publicId()).orElseThrow(this::invalid);
        // Compare the digest before returning tenant/status decisions.
        if (!crypto.matches(raw, key.getSecretHash()) || key.getStatus() != ApiKey.Status.ACTIVE
                || (key.getExpiresAt() != null && !key.getExpiresAt().isAfter(Instant.now()))) throw invalid();
        if (key.getTenant().getStatus() != Tenant.Status.ACTIVE) throw new AccessDeniedException("Access denied");
        if (keys.recordUse(key.getId(), Instant.now(), ApiKey.Status.ACTIVE) != 1) throw invalid();
        return new GatewayPrincipal(key.getTenant().getId(), key.getId(), key.getPermissions());
    }
    private BadCredentialsException invalid() { return new BadCredentialsException("Invalid API key"); }
}
