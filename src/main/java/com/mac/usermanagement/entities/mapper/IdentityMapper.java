package com.mac.usermanagement.entities.mapper;

import com.mac.usermanagement.entities.dto.PermissionResponse;
import com.mac.usermanagement.entities.dto.RoleResponse;
import com.mac.usermanagement.entities.dto.UserResponse;
import com.mac.usermanagement.entities.model.Permission;
import com.mac.usermanagement.entities.model.Role;
import com.mac.usermanagement.entities.model.UserAccount;
import java.util.Set;

public final class IdentityMapper {

    private IdentityMapper() {}

    public static UserResponse toResponse(UserAccount user, Set<String> roles) {
        return new UserResponse(user.id(), user.tenantId(), user.username(), user.email(), user.enabled(),
                Set.copyOf(roles), user.createdAt());
    }

    public static RoleResponse toResponse(Role role) {
        return new RoleResponse(role.id(), role.tenantId(), role.name(), role.description(), role.systemRole(),
                Set.copyOf(role.permissions()), role.createdAt());
    }

    public static PermissionResponse toResponse(Permission permission) {
        return new PermissionResponse(permission.id(), permission.resource(), permission.action(),
                permission.authority(), permission.description());
    }
}
