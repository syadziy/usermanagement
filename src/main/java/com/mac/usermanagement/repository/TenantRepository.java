package com.mac.usermanagement.repository;

import com.mac.usermanagement.entities.model.Tenant;
import java.util.Optional;
import java.util.UUID;

public interface TenantRepository {

    Tenant insert(Tenant tenant);

    Optional<Tenant> findById(UUID tenantId);

    Optional<Tenant> findByKey(String tenantKey);

    void updateTokenTtl(UUID tenantId, long ttlSeconds);
}
