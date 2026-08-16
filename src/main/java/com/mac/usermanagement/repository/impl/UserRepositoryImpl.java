package com.mac.usermanagement.repository.impl;

import com.mac.usermanagement.entities.model.UserAccess;
import com.mac.usermanagement.entities.model.UserAccount;
import com.mac.usermanagement.repository.UserRepository;
import com.mac.usermanagement.utils.exception.IdentityConflictException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class UserRepositoryImpl implements UserRepository {

    private static final String SELECT = """
            SELECT id, tenant_id, username, email, password_hash, enabled, created_at, updated_at
            FROM user_account
            """;
    private final NamedParameterJdbcTemplate jdbcTemplate;

    public UserRepositoryImpl(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public UserAccount insert(UserAccount user) {
        try {
            jdbcTemplate.update("""
                    INSERT INTO user_account (
                        id, tenant_id, username, email, password_hash, enabled, created_at, updated_at
                    ) VALUES (
                        :id, :tenantId, :username, :email, :passwordHash, :enabled, :createdAt, :updatedAt
                    )
                    """, parameters(user));
            return user;
        } catch (DuplicateKeyException exception) {
            throw new IdentityConflictException("Username or email already exists in this tenant", exception);
        }
    }

    @Override
    public Optional<UserAccount> findByUsername(UUID tenantId, String username) {
        return query(SELECT + " WHERE tenant_id = :tenantId AND LOWER(username) = LOWER(:username)",
                Map.of("tenantId", tenantId, "username", username));
    }

    @Override
    public Optional<UserAccount> findById(UUID tenantId, UUID userId) {
        return query(SELECT + " WHERE tenant_id = :tenantId AND id = :userId",
                Map.of("tenantId", tenantId, "userId", userId));
    }

    @Override
    public List<UserAccount> findAll(UUID tenantId) {
        return jdbcTemplate.query(SELECT + " WHERE tenant_id = :tenantId ORDER BY username LIMIT 100",
                Map.of("tenantId", tenantId), this::map);
    }

    @Override
    public List<UserAccount> findAll(UUID tenantId, int limit, int offset) {
        return jdbcTemplate.query(SELECT + """
                 WHERE tenant_id = :tenantId
                 ORDER BY username, id
                 LIMIT :limit OFFSET :offset
                """, Map.of("tenantId", tenantId, "limit", limit, "offset", offset), this::map);
    }

    @Override
    public long count(UUID tenantId) {
        Long total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM user_account WHERE tenant_id = :tenantId",
                Map.of("tenantId", tenantId), Long.class);
        return total == null ? 0 : total;
    }

    @Override
    public UserAccess findAccess(UUID tenantId, UUID userId) {
        return findAccess(tenantId, Set.of(userId))
                .getOrDefault(userId, new UserAccess(Set.of(), Set.of()));
    }

    @Override
    public Map<UUID, UserAccess> findAccess(UUID tenantId, Set<UUID> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, Set<String>> rolesByUser = new HashMap<>();
        Map<UUID, Set<String>> permissionsByUser = new HashMap<>();
        userIds.forEach(userId -> {
            rolesByUser.put(userId, new TreeSet<>());
            permissionsByUser.put(userId, new TreeSet<>());
        });
        jdbcTemplate.query("""
                SELECT ur.user_id, r.name AS role_name, p.resource, p.action
                FROM user_role ur
                JOIN role r ON r.id = ur.role_id AND r.tenant_id = ur.tenant_id
                LEFT JOIN role_permission rp ON rp.role_id = r.id AND rp.tenant_id = r.tenant_id
                LEFT JOIN permission p ON p.id = rp.permission_id
                WHERE ur.tenant_id = :tenantId AND ur.user_id IN (:userIds)
                ORDER BY ur.user_id, r.name, p.resource, p.action
                """, Map.of("tenantId", tenantId, "userIds", userIds), resultSet -> {
            UUID userId = resultSet.getObject("user_id", UUID.class);
            rolesByUser.get(userId).add(resultSet.getString("role_name"));
            String resource = resultSet.getString("resource");
            if (resource != null) {
                permissionsByUser.get(userId).add(resource + ":" + resultSet.getString("action"));
            }
        });
        Map<UUID, UserAccess> accessByUser = new HashMap<>();
        userIds.forEach(userId -> accessByUser.put(userId,
                new UserAccess(Set.copyOf(rolesByUser.get(userId)),
                        Set.copyOf(permissionsByUser.get(userId)))));
        return Map.copyOf(accessByUser);
    }

    @Override
    public void replaceRoles(UUID tenantId, UUID userId, Set<UUID> roleIds, Instant assignedAt) {
        jdbcTemplate.update("DELETE FROM user_role WHERE tenant_id = :tenantId AND user_id = :userId",
                Map.of("tenantId", tenantId, "userId", userId));
        MapSqlParameterSource[] batch = roleIds.stream().map(roleId -> new MapSqlParameterSource()
                .addValue("tenantId", tenantId).addValue("userId", userId)
                .addValue("roleId", roleId)
                .addValue("assignedAt", assignedAt.atOffset(ZoneOffset.UTC), Types.TIMESTAMP_WITH_TIMEZONE))
                .toArray(MapSqlParameterSource[]::new);
        jdbcTemplate.batchUpdate("""
                INSERT INTO user_role (tenant_id, user_id, role_id, assigned_at)
                VALUES (:tenantId, :userId, :roleId, :assignedAt)
                """, batch);
    }

    private Optional<UserAccount> query(String sql, Map<String, ?> parameters) {
        return jdbcTemplate.query(sql, parameters, this::map).stream().findFirst();
    }

    private MapSqlParameterSource parameters(UserAccount user) {
        return new MapSqlParameterSource().addValue("id", user.id()).addValue("tenantId", user.tenantId())
                .addValue("username", user.username()).addValue("email", user.email())
                .addValue("passwordHash", user.passwordHash()).addValue("enabled", user.enabled())
                .addValue("createdAt", user.createdAt().atOffset(ZoneOffset.UTC), Types.TIMESTAMP_WITH_TIMEZONE)
                .addValue("updatedAt", user.updatedAt().atOffset(ZoneOffset.UTC), Types.TIMESTAMP_WITH_TIMEZONE);
    }

    private UserAccount map(ResultSet resultSet, int rowNumber) throws SQLException {
        return new UserAccount(resultSet.getObject("id", UUID.class),
                resultSet.getObject("tenant_id", UUID.class), resultSet.getString("username"),
                resultSet.getString("email"), resultSet.getString("password_hash"),
                resultSet.getBoolean("enabled"), resultSet.getTimestamp("created_at").toInstant(),
                resultSet.getTimestamp("updated_at").toInstant());
    }
}
