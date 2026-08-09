package com.mac.usermanagement.entities.dto;

import java.util.UUID;

public record PermissionResponse(
        UUID permissionId,
        String resource,
        String action,
        String authority,
        String description) {}
