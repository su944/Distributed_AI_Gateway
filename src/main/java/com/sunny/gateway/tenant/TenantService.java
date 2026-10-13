package com.sunny.gateway.tenant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class TenantService {
    private final TenantRepository repository;
    public TenantService(TenantRepository repository) { this.repository = repository; }
    @Transactional
    public TenantSummary create(String name, String slug) {
        if (name == null || name.isBlank() || name.length() > 100 || slug == null
                || !slug.matches("[a-z0-9]+(?:-[a-z0-9]+)*") || slug.length() > 100) {
            throw new IllegalArgumentException("Invalid tenant name or slug");
        }
        name = name.strip();
        if (repository.existsByNameOrSlug(name, slug)) throw new IllegalArgumentException("Tenant identifier already exists");
        return summary(repository.saveAndFlush(new Tenant(name, slug, Instant.now())));
    }
    @Transactional(readOnly = true)
    public List<TenantSummary> list() { return repository.findAll().stream().map(this::summary).toList(); }
    @Transactional
    public void changeStatus(UUID tenantId, Tenant.Status status) {
        var tenant = repository.findById(tenantId).orElseThrow(() -> new IllegalArgumentException("Tenant not found"));
        tenant.changeStatus(status, Instant.now());
    }
    private TenantSummary summary(Tenant tenant) {
        return new TenantSummary(tenant.getId(), tenant.getName(), tenant.getSlug(), tenant.getStatus());
    }
    public record TenantSummary(UUID id, String name, String slug, Tenant.Status status) {}
}
