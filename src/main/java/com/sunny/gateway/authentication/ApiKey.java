package com.sunny.gateway.authentication;

import com.sunny.gateway.tenant.Tenant;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.Arrays;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Entity @Table(name = "api_keys")
public class ApiKey {
    public enum Status { ACTIVE, REVOKED }
    @Id private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "tenant_id", nullable = false) private Tenant tenant;
    @Column(nullable = false, length = 100) private String name;
    @Column(nullable = false, unique = true, length = 32) private String publicId;
    @Column(nullable = false, length = 64) private String secretHash;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16) private Status status;
    @Column(nullable = false, length = 100) private String permissions;
    @Column(nullable = false) private Instant createdAt;
    private Instant expiresAt;
    private Instant lastUsedAt;
    private Instant revokedAt;
    protected ApiKey() {}
    ApiKey(Tenant tenant, String name, String publicId, String secretHash, Set<Permission> permissions, Instant now, Instant expiresAt) {
        this.id = UUID.randomUUID(); this.tenant = tenant; this.name = name; this.publicId = publicId;
        this.secretHash = secretHash; this.status = Status.ACTIVE;
        this.permissions = permissions.stream().map(Enum::name).sorted().collect(Collectors.joining(","));
        this.createdAt = now; this.expiresAt = expiresAt;
    }
    public UUID getId() { return id; }
    public Tenant getTenant() { return tenant; }
    public String getName() { return name; }
    public String getPublicId() { return publicId; }
    String getSecretHash() { return secretHash; }
    public Status getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getLastUsedAt() { return lastUsedAt; }
    public Instant getRevokedAt() { return revokedAt; }
    public Set<Permission> getPermissions() {
        return permissions.isEmpty() ? Set.of() : Arrays.stream(permissions.split(",")).map(Permission::valueOf).collect(Collectors.toUnmodifiableSet());
    }
    public void revoke(Instant now) { this.status = Status.REVOKED; this.revokedAt = now; }
}
