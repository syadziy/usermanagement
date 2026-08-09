package com.mac.usermanagement.entities.dto;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record RoleResponse(
        UUID roleId,
        UUID tenantId,
        String name,
        String description,
        boolean systemRole,
        Set<String> permissions,
        Instant createdAt) {}
