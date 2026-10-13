package com.sunny.gateway.authentication;

import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ApiKeyRepository extends JpaRepository<ApiKey, UUID> {
    @EntityGraph(attributePaths = "tenant")
    Optional<ApiKey> findByPublicId(String publicId);
    Optional<ApiKey> findByIdAndTenant_Id(UUID id, UUID tenantId);
    List<ApiKey> findAllByTenant_IdOrderByCreatedAtDesc(UUID tenantId);
    @Modifying
    @Query("update ApiKey k set k.lastUsedAt = :now where k.id = :id and k.status = :status")
    int recordUse(@Param("id") UUID id, @Param("now") Instant now, @Param("status") ApiKey.Status status);
}
