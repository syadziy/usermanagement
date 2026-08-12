package com.mac.usermanagement.repository;

import com.mac.usermanagement.entities.model.Tenant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TenantRepository {

    Tenant insert(Tenant tenant);

    Optional<Tenant> findById(UUID tenantId);

    Optional<Tenant> findByKey(String tenantKey);

    List<Tenant> findAll();

    List<Tenant> findAll(int limit, int offset);

    long count();

    void updateTokenTtl(UUID tenantId, long ttlSeconds);
}
