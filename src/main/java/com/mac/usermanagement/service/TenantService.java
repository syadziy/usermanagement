package com.mac.usermanagement.service;

import com.mac.usermanagement.entities.dto.RegisterTenantRequest;
import com.mac.usermanagement.entities.dto.TenantListResponse;
import com.mac.usermanagement.entities.dto.TenantResponse;
import java.util.List;
import java.util.UUID;

public interface TenantService {

    TenantResponse register(RegisterTenantRequest request);

    List<TenantListResponse> findAll(int limit, int offset);

    long count();

    void updateTokenPolicy(UUID tenantId, long ttlSeconds);
}
