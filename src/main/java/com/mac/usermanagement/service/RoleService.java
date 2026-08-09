package com.mac.usermanagement.service;

import com.mac.usermanagement.entities.dto.CreatePermissionRequest;
import com.mac.usermanagement.entities.dto.CreateRoleRequest;
import com.mac.usermanagement.entities.dto.PermissionResponse;
import com.mac.usermanagement.entities.dto.RoleResponse;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface RoleService {

    RoleResponse createRole(UUID tenantId, CreateRoleRequest request);

    List<RoleResponse> findRoles(UUID tenantId);

    RoleResponse replacePermissions(UUID tenantId, UUID roleId, Set<String> permissions);

    PermissionResponse createPermission(UUID tenantId, CreatePermissionRequest request);

    List<PermissionResponse> findPermissions(UUID tenantId);
}
