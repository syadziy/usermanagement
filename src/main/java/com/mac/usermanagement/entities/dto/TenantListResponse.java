package com.mac.usermanagement.entities.dto;

import java.time.Instant;
import java.util.UUID;

public record TenantListResponse(
        UUID tenantId,
        String tenantKey,
        String tenantName,
        long accessTokenTtlSeconds,
        boolean enabled,
        Instant createdAt) {}
