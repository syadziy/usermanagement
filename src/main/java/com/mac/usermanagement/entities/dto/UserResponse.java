package com.mac.usermanagement.entities.dto;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record UserResponse(
        UUID userId,
        UUID tenantId,
        String username,
        String email,
        boolean enabled,
        Set<String> roles,
        Instant createdAt) {}
