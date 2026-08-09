package com.mac.usermanagement.entities.dto;

import java.time.Instant;
import java.util.UUID;

public record TenantResponse(
        UUID tenantId,
        String tenantKey,
        String tenantName,
        long accessTokenTtlSeconds,
        UUID ownerUserId,
        Instant createdAt) {}
