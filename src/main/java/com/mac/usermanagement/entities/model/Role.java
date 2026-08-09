package com.mac.usermanagement.entities.model;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record Role(
        UUID id,
        UUID tenantId,
        String name,
        String description,
        boolean systemRole,
        Set<String> permissions,
        Instant createdAt) {}
