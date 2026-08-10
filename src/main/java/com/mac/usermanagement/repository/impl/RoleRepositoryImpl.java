package com.mac.usermanagement.repository.impl;

import com.mac.usermanagement.entities.constant.AuthorizationCatalog.PermissionDefinition;
import com.mac.usermanagement.entities.model.Permission;
import com.mac.usermanagement.entities.model.Role;
import com.mac.usermanagement.repository.RoleRepository;
import com.mac.usermanagement.utils.exception.IdentityConflictException;
import java.sql.Types;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class RoleRepositoryImpl implements RoleRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public RoleRepositoryImpl(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Role insert(Role role) {
        try {
            jdbcTemplate.update("""
                    INSERT INTO role (id, tenant_id, name, description, system_role, created_at)
                    VALUES (:id, :tenantId, :name, :description, :systemRole, :createdAt)
                    """, new MapSqlParameterSource().addValue("id", role.id())
                    .addValue("tenantId", role.tenantId()).addValue("name", role.name())
                    .addValue("description", role.description()).addValue("systemRole", role.systemRole())
                    .addValue("createdAt", role.createdAt().atOffset(ZoneOffset.UTC),
                            Types.TIMESTAMP_WITH_TIMEZONE));
            return role;
        } catch (DuplicateKeyException exception) {
            throw new IdentityConflictException("Role name already exists in this tenant", exception);
        }
    }

    @Override
    public Permission insertPermission(Permission permission) {
        try {
            jdbcTemplate.update("""
                    INSERT INTO permission (id, tenant_id, resource, action, description, created_at)
                    VALUES (:id, :tenantId, :resource, :action, :description, :createdAt)
                    """, new MapSqlParameterSource().addValue("id", permission.id())
                    .addValue("tenantId", permission.tenantId()).addValue("resource", permission.resource())
                    .addValue("action", permission.action()).addValue("description", permission.description())
                    .addValue("createdAt", permission.createdAt().atOffset(ZoneOffset.UTC),
                            Types.TIMESTAMP_WITH_TIMEZONE));
            return permission;
        } catch (DuplicateKeyException exception) {
            throw new IdentityConflictException("Permission already exists in this tenant", exception);
        }
    }

    @Override
    public void seedPermissions(UUID tenantId, List<PermissionDefinition> definitions, Instant createdAt) {
        for (PermissionDefinition definition : definitions) {
            insertPermission(new Permission(UUID.randomUUID(), tenantId, definition.resource(),
                    definition.action(), definition.description(), createdAt));
        }
    }

    @Override
    public void assignAllPermissions(UUID tenantId, UUID roleId, Instant assignedAt) {
        jdbcTemplate.update("""
                INSERT INTO role_permission (tenant_id, role_id, permission_id, assigned_at)
                SELECT :tenantId, :roleId, id, :assignedAt FROM permission WHERE tenant_id = :tenantId
                """, new MapSqlParameterSource().addValue("tenantId", tenantId)
                .addValue("roleId", roleId)
                .addValue("assignedAt", assignedAt.atOffset(ZoneOffset.UTC), Types.TIMESTAMP_WITH_TIMEZONE));
    }

    @Override
    public void replacePermissions(UUID tenantId, UUID roleId, Set<String> authorities, Instant assignedAt) {
        Map<String, UUID> known = new LinkedHashMap<>();
        for (Permission permission : findPermissions(tenantId)) {
            known.put(permission.authority(), permission.id());
        }
        if (!known.keySet().containsAll(authorities)) {
            Set<String> unknown = new LinkedHashSet<>(authorities);
            unknown.removeAll(known.keySet());
            throw new IllegalArgumentException("Unknown permissions: " + String.join(", ", unknown));
        }
        jdbcTemplate.update("DELETE FROM role_permission WHERE tenant_id = :tenantId AND role_id = :roleId",
                Map.of("tenantId", tenantId, "roleId", roleId));
        MapSqlParameterSource[] batch = authorities.stream().map(authority -> new MapSqlParameterSource()
                .addValue("tenantId", tenantId).addValue("roleId", roleId)
                .addValue("permissionId", known.get(authority))
                .addValue("assignedAt", assignedAt.atOffset(ZoneOffset.UTC), Types.TIMESTAMP_WITH_TIMEZONE))
                .toArray(MapSqlParameterSource[]::new);
        jdbcTemplate.batchUpdate("""
                INSERT INTO role_permission (tenant_id, role_id, permission_id, assigned_at)
                VALUES (:tenantId, :roleId, :permissionId, :assignedAt)
                """, batch);
    }

    @Override
    public Optional<Role> findById(UUID tenantId, UUID roleId) {
        return findRoles(tenantId, roleId).stream().findFirst();
    }

    @Override
    public List<Role> findAll(UUID tenantId) {
        return findRoles(tenantId, null);
    }

    private List<Role> findRoles(UUID tenantId, UUID roleId) {
        MapSqlParameterSource parameters = new MapSqlParameterSource().addValue("tenantId", tenantId);
        String rolePredicate = "";
        if (roleId != null) {
            rolePredicate = " AND r.id = :roleId";
            parameters.addValue("roleId", roleId);
        }
        Map<UUID, MutableRole> roles = new LinkedHashMap<>();
        jdbcTemplate.query("""
                SELECT r.id, r.tenant_id, r.name, r.description, r.system_role, r.created_at,
                       p.resource, p.action
                FROM role r
                LEFT JOIN role_permission rp ON rp.role_id = r.id AND rp.tenant_id = r.tenant_id
                LEFT JOIN permission p ON p.id = rp.permission_id AND p.tenant_id = rp.tenant_id
                WHERE r.tenant_id = :tenantId
                """ + rolePredicate + " ORDER BY r.name, p.resource, p.action", parameters, resultSet -> {
            UUID id = resultSet.getObject("id", UUID.class);
            UUID rowTenantId = resultSet.getObject("tenant_id", UUID.class);
            String name = resultSet.getString("name");
            String description = resultSet.getString("description");
            boolean systemRole = resultSet.getBoolean("system_role");
            Instant createdAt = resultSet.getTimestamp("created_at").toInstant();
            MutableRole role = roles.computeIfAbsent(id, ignored -> new MutableRole(
                    id, rowTenantId, name, description, systemRole, createdAt));
            String resource = resultSet.getString("resource");
            if (resource != null) {
                role.permissions.add(resource + ":" + resultSet.getString("action"));
            }
        });
        return roles.values().stream().map(MutableRole::toRole).toList();
    }

    @Override
    public List<Permission> findPermissions(UUID tenantId) {
        return jdbcTemplate.query("""
                SELECT id, tenant_id, resource, action, description, created_at
                FROM permission WHERE tenant_id = :tenantId ORDER BY resource, action
                """, Map.of("tenantId", tenantId), (resultSet, rowNumber) -> new Permission(
                resultSet.getObject("id", UUID.class), resultSet.getObject("tenant_id", UUID.class),
                resultSet.getString("resource"), resultSet.getString("action"),
                resultSet.getString("description"), resultSet.getTimestamp("created_at").toInstant()));
    }

    @Override
    public boolean allExist(UUID tenantId, Set<UUID> roleIds) {
        if (roleIds.isEmpty()) {
            return true;
        }
        Long count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM role WHERE tenant_id = :tenantId AND id IN (:roleIds)
                """, Map.of("tenantId", tenantId, "roleIds", roleIds), Long.class);
        return count != null && count == roleIds.size();
    }

    private static final class MutableRole {
        private final UUID id;
        private final UUID tenantId;
        private final String name;
        private final String description;
        private final boolean systemRole;
        private final Instant createdAt;
        private final Set<String> permissions = new LinkedHashSet<>();

        private MutableRole(UUID id, UUID tenantId, String name, String description,
                boolean systemRole, Instant createdAt) {
            this.id = id;
            this.tenantId = tenantId;
            this.name = name;
            this.description = description;
            this.systemRole = systemRole;
            this.createdAt = createdAt;
        }

        private Role toRole() {
            return new Role(id, tenantId, name, description, systemRole, Set.copyOf(permissions), createdAt);
        }
    }
}
