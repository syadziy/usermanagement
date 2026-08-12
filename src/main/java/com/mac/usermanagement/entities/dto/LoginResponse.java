package com.mac.usermanagement.entities.dto;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record LoginResponse(
        String tokenType,
        String accessToken,
        Instant expiresAt,
        UUID tenantId,
        String tenantKey,
        UUID userId,
        Set<String> roles,
        Set<String> permissions) {}
