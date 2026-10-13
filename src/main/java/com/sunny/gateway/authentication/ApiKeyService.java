package com.sunny.gateway.authentication;

import com.sunny.gateway.tenant.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.*;

@Service
public class ApiKeyService {
    private final ApiKeyRepository keys;
    private final TenantRepository tenants;
    private final ApiKeyCrypto crypto;
    public ApiKeyService(ApiKeyRepository keys, TenantRepository tenants, ApiKeyCrypto crypto) {
        this.keys = keys; this.tenants = tenants; this.crypto = crypto;
    }
    @Transactional
    public IssuedKey issue(UUID tenantId, String name, Set<Permission> permissions, Instant expiresAt) {
        if (name == null || name.isBlank() || name.length() > 100 || permissions == null) throw new IllegalArgumentException("Invalid key metadata");
        if (expiresAt != null && !expiresAt.isAfter(Instant.now())) throw new IllegalArgumentException("Expiry must be in the future");
        var tenant = tenants.findById(tenantId).orElseThrow(() -> new IllegalArgumentException("Tenant not found"));
        if (tenant.getStatus() != Tenant.Status.ACTIVE) throw new IllegalArgumentException("Tenant is suspended");
        var generated = crypto.generate();
        var key = keys.saveAndFlush(new ApiKey(tenant, name.strip(), generated.publicId(), generated.digest(), permissions, Instant.now(), expiresAt));
        return new IssuedKey(metadata(key), generated.raw());
    }
    @Transactional(readOnly = true)
    public List<KeyMetadata> list(UUID tenantId) {
        return keys.findAllByTenant_IdOrderByCreatedAtDesc(tenantId).stream().map(this::metadata).toList();
    }
    @Transactional(readOnly = true)
    public KeyMetadata get(UUID tenantId, UUID keyId) { return metadata(scoped(tenantId, keyId)); }
    @Transactional
    public void revoke(UUID tenantId, UUID keyId) {
        var key = scoped(tenantId, keyId);
        if (key.getStatus() != ApiKey.Status.REVOKED) key.revoke(Instant.now());
    }
    private ApiKey scoped(UUID tenantId, UUID keyId) {
        return keys.findByIdAndTenant_Id(keyId, tenantId).orElseThrow(() -> new IllegalArgumentException("API key not found"));
    }
    private KeyMetadata metadata(ApiKey key) {
        return new KeyMetadata(key.getId(), key.getTenant().getId(), key.getName(), key.getPublicId(), key.getStatus(),
                key.getPermissions(), key.getCreatedAt(), key.getExpiresAt(), key.getLastUsedAt(), key.getRevokedAt());
    }
    public record KeyMetadata(UUID id, UUID tenantId, String name, String publicId, ApiKey.Status status,
            Set<Permission> permissions, Instant createdAt, Instant expiresAt, Instant lastUsedAt, Instant revokedAt) {}
    public record IssuedKey(KeyMetadata metadata, String plaintext) {
        @Override public String toString() { return "IssuedKey[metadata=" + metadata + ", plaintext=REDACTED]"; }
    }
}
