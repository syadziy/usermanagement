package com.mac.usermanagement.entities.model;

import java.time.Instant;
import java.util.UUID;

public record Tenant(
        UUID id,
        String tenantKey,
        String name,
        long accessTokenTtlSeconds,
        boolean enabled,
        Instant createdAt) {}
