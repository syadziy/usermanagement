package com.mac.usermanagement.repository;

import com.mac.usermanagement.entities.constant.AuthorizationCatalog.PermissionDefinition;
import com.mac.usermanagement.entities.model.Permission;
import com.mac.usermanagement.entities.model.Role;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface RoleRepository {

    Role insert(Role role);

    Permission insertPermission(Permission permission);

    void seedPermissions(UUID tenantId, List<PermissionDefinition> definitions, Instant createdAt);

    void assignAllPermissions(UUID tenantId, UUID roleId, Instant assignedAt);

    void replacePermissions(UUID tenantId, UUID roleId, Set<String> authorities, Instant assignedAt);

    Optional<Role> findById(UUID tenantId, UUID roleId);

    List<Role> findAll(UUID tenantId);

    List<Role> findAll(UUID tenantId, int limit, int offset);

    long countRoles(UUID tenantId);

    List<Permission> findPermissions(UUID tenantId);

    List<Permission> findPermissions(UUID tenantId, int limit, int offset);

    long countPermissions(UUID tenantId);

    boolean allExist(UUID tenantId, Set<UUID> roleIds);
}
