package com.mac.usermanagement.entities.model;

import java.time.Instant;
import java.util.UUID;

public record UserAccount(
        UUID id,
        UUID tenantId,
        String username,
        String email,
        String passwordHash,
        boolean enabled,
        Instant createdAt,
        Instant updatedAt) {}
