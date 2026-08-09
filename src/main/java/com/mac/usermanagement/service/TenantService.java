package com.mac.usermanagement.service;

import com.mac.usermanagement.entities.dto.RegisterTenantRequest;
import com.mac.usermanagement.entities.dto.TenantResponse;
import java.util.UUID;

public interface TenantService {

    TenantResponse register(RegisterTenantRequest request);

    void updateTokenPolicy(UUID tenantId, long ttlSeconds);
}
