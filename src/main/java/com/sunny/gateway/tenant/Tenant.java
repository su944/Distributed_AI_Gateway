package com.sunny.gateway.tenant;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name = "tenants")
public class Tenant {
    public enum Status { ACTIVE, SUSPENDED }
    @Id private UUID id;
    @Column(nullable = false, unique = true, length = 100) private String name;
    @Column(nullable = false, unique = true, length = 100) private String slug;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16) private Status status;
    @Column(nullable = false) private Instant createdAt;
    @Column(nullable = false) private Instant updatedAt;
    protected Tenant() {}
    public Tenant(String name, String slug, Instant now) {
        this.id = UUID.randomUUID(); this.name = name; this.slug = slug;
        this.status = Status.ACTIVE; this.createdAt = now; this.updatedAt = now;
    }
    public UUID getId() { return id; }
    public String getName() { return name; }
    public String getSlug() { return slug; }
    public Status getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void changeStatus(Status status, Instant now) { this.status = status; this.updatedAt = now; }
}
