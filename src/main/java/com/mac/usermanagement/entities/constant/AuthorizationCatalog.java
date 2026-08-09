package com.mac.usermanagement.entities.constant;

import java.util.List;

public final class AuthorizationCatalog {

    public static final String OWNER_ROLE = "TENANT_OWNER";
    public static final List<PermissionDefinition> DEFAULT_PERMISSIONS = List.of(
            permission("tenant", "view"), permission("tenant", "update"),
            permission("user", "view"), permission("user", "create"),
            permission("user", "edit"), permission("user", "delete"),
            permission("user", "download"), permission("user", "upload"),
            permission("role", "view"), permission("role", "create"),
            permission("role", "edit"), permission("role", "delete"),
            permission("role", "assign"), permission("permission", "view"),
            permission("permission", "create"));

    private AuthorizationCatalog() {}

    private static PermissionDefinition permission(String resource, String action) {
        return new PermissionDefinition(resource, action, "Allows " + action + " on " + resource);
    }

    public record PermissionDefinition(String resource, String action, String description) {}
}
