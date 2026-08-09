package com.mac.usermanagement.repository.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.mac.sdk_util.exception.ResourceNotFoundException;
import com.mac.usermanagement.entities.constant.AuthorizationCatalog.PermissionDefinition;
import com.mac.usermanagement.entities.model.*;
import com.mac.usermanagement.utils.exception.IdentityConflictException;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

class RepositoryCoverageTest {

    private static final UUID TENANT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID USER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID ROLE_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID PERMISSION_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final Instant NOW = Instant.parse("2026-08-09T15:00:00Z");

    @Test
    void tenantRepositoryCoversInsertQueriesUpdateAndConflicts() throws Exception {
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        TenantRepositoryImpl repository = new TenantRepositoryImpl(jdbc);
        Tenant tenant = tenant();
        assertSame(tenant, repository.insert(tenant));

        when(jdbc.query(anyString(), anyMap(), any(RowMapper.class))).thenAnswer(invocation -> {
            RowMapper<Tenant> mapper = invocation.getArgument(2);
            return List.of(mapper.mapRow(tenantResultSet(), 0));
        });
        assertEquals(tenant, repository.findById(TENANT_ID).orElseThrow());
        assertEquals(tenant, repository.findByKey("acme-id").orElseThrow());

        when(jdbc.update(startsWith("UPDATE tenant"), anyMap())).thenReturn(1, 0);
        repository.updateTokenTtl(TENANT_ID, 3600);
        assertThrows(ResourceNotFoundException.class, () -> repository.updateTokenTtl(TENANT_ID, 3600));

        when(jdbc.update(startsWith("INSERT INTO tenant"), any(MapSqlParameterSource.class)))
                .thenThrow(new DuplicateKeyException("duplicate"));
        assertThrows(IdentityConflictException.class, () -> repository.insert(tenant));
    }

    @Test
    void userRepositoryCoversInsertQueriesAccessRolesAndConflicts() throws Exception {
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        UserRepositoryImpl repository = new UserRepositoryImpl(jdbc);
        UserAccount user = user();
        assertSame(user, repository.insert(user));

        when(jdbc.query(anyString(), anyMap(), any(RowMapper.class))).thenAnswer(invocation -> {
            RowMapper<UserAccount> mapper = invocation.getArgument(2);
            return List.of(mapper.mapRow(userResultSet(), 0));
        });
        assertEquals(user, repository.findByUsername(TENANT_ID, "owner").orElseThrow());
        assertEquals(user, repository.findById(TENANT_ID, USER_ID).orElseThrow());
        assertEquals(List.of(user), repository.findAll(TENANT_ID));

        doAnswer(invocation -> {
            RowCallbackHandler callback = invocation.getArgument(2);
            ResultSet resultSet = mock(ResultSet.class);
            when(resultSet.getObject("user_id", UUID.class)).thenReturn(USER_ID);
            when(resultSet.getString("role_name")).thenReturn("TENANT_OWNER");
            when(resultSet.getString("resource")).thenReturn("tenant");
            when(resultSet.getString("action")).thenReturn("update");
            callback.processRow(resultSet);
            return null;
        }).when(jdbc).query(anyString(), anyMap(), any(RowCallbackHandler.class));
        UserAccess access = repository.findAccess(TENANT_ID, USER_ID);
        assertEquals(Set.of("TENANT_OWNER"), access.roles());
        assertEquals(Set.of("tenant:update"), access.permissions());
        assertEquals(access, repository.findAccess(TENANT_ID, Set.of(USER_ID)).get(USER_ID));
        assertTrue(repository.findAccess(TENANT_ID, Set.of()).isEmpty());

        repository.replaceRoles(TENANT_ID, USER_ID, Set.of(ROLE_ID), NOW);
        verify(jdbc).batchUpdate(contains("INSERT INTO user_role"), any(MapSqlParameterSource[].class));

        when(jdbc.update(startsWith("INSERT INTO user_account"), any(MapSqlParameterSource.class)))
                .thenThrow(new DuplicateKeyException("duplicate"));
        assertThrows(IdentityConflictException.class, () -> repository.insert(user));
    }

    @Test
    void userAccessHandlesRoleWithoutPermission() throws Exception {
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        doAnswer(invocation -> {
            RowCallbackHandler callback = invocation.getArgument(2);
            ResultSet resultSet = mock(ResultSet.class);
            when(resultSet.getObject("user_id", UUID.class)).thenReturn(USER_ID);
            when(resultSet.getString("role_name")).thenReturn("EMPTY");
            when(resultSet.getString("resource")).thenReturn(null);
            callback.processRow(resultSet);
            return null;
        }).when(jdbc).query(anyString(), anyMap(), any(RowCallbackHandler.class));
        UserAccess access = new UserRepositoryImpl(jdbc).findAccess(TENANT_ID, USER_ID);
        assertEquals(Set.of("EMPTY"), access.roles());
        assertTrue(access.permissions().isEmpty());
    }

    @Test
    void roleRepositoryCoversWritesReadsPermissionsAndValidation() throws Exception {
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        RoleRepositoryImpl repository = new RoleRepositoryImpl(jdbc);
        Role role = role();
        Permission permission = permission();
        assertSame(role, repository.insert(role));
        assertSame(permission, repository.insertPermission(permission));
        repository.seedPermissions(TENANT_ID,
                List.of(new PermissionDefinition("user", "view", "view users")), NOW);
        repository.assignAllPermissions(TENANT_ID, ROLE_ID, NOW);

        doAnswer(invocation -> {
            RowCallbackHandler callback = invocation.getArgument(2);
            callback.processRow(roleResultSet("user", "view"));
            return null;
        }).when(jdbc).query(anyString(), any(MapSqlParameterSource.class), any(RowCallbackHandler.class));
        assertEquals(Set.of("user:view"), repository.findById(TENANT_ID, ROLE_ID).orElseThrow().permissions());
        assertEquals(1, repository.findAll(TENANT_ID).size());

        when(jdbc.query(contains("FROM permission WHERE"), anyMap(), any(RowMapper.class)))
                .thenAnswer(invocation -> {
                    RowMapper<Permission> mapper = invocation.getArgument(2);
                    return List.of(mapper.mapRow(permissionResultSet(), 0));
                });
        assertEquals(List.of(permission), repository.findPermissions(TENANT_ID));
        repository.replacePermissions(TENANT_ID, ROLE_ID, Set.of("user:view"), NOW);
        verify(jdbc).batchUpdate(contains("INSERT INTO role_permission"), any(MapSqlParameterSource[].class));
        assertThrows(IllegalArgumentException.class,
                () -> repository.replacePermissions(TENANT_ID, ROLE_ID, Set.of("unknown:action"), NOW));

        when(jdbc.queryForObject(contains("COUNT(*) FROM role"), anyMap(), eq(Long.class)))
                .thenReturn(1L, 0L, null);
        assertTrue(repository.allExist(TENANT_ID, Set.of(ROLE_ID)));
        assertFalse(repository.allExist(TENANT_ID, Set.of(ROLE_ID)));
        assertFalse(repository.allExist(TENANT_ID, Set.of(ROLE_ID)));
        assertTrue(repository.allExist(TENANT_ID, Set.of()));

        when(jdbc.update(startsWith("INSERT INTO role ("), any(MapSqlParameterSource.class)))
                .thenThrow(new DuplicateKeyException("duplicate"));
        assertThrows(IdentityConflictException.class, () -> repository.insert(role));
        when(jdbc.update(startsWith("INSERT INTO permission"), any(MapSqlParameterSource.class)))
                .thenThrow(new DuplicateKeyException("duplicate"));
        assertThrows(IdentityConflictException.class, () -> repository.insertPermission(permission));
    }

    @Test
    void roleMappingHandlesNoPermissions() throws Exception {
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        doAnswer(invocation -> {
            RowCallbackHandler callback = invocation.getArgument(2);
            callback.processRow(roleResultSet(null, null));
            return null;
        }).when(jdbc).query(anyString(), any(MapSqlParameterSource.class), any(RowCallbackHandler.class));
        assertTrue(new RoleRepositoryImpl(jdbc).findAll(TENANT_ID).getFirst().permissions().isEmpty());
    }

    private static Tenant tenant() {
        return new Tenant(TENANT_ID, "acme-id", "ACME", 1800, true, NOW);
    }

    private static UserAccount user() {
        return new UserAccount(USER_ID, TENANT_ID, "owner", "owner@example.com", "hash", true, NOW, NOW);
    }

    private static Role role() {
        return new Role(ROLE_ID, TENANT_ID, "TENANT_OWNER", "owner", true, Set.of("user:view"), NOW);
    }

    private static Permission permission() {
        return new Permission(PERMISSION_ID, TENANT_ID, "user", "view", "view users", NOW);
    }

    private static ResultSet tenantResultSet() throws Exception {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getObject("id", UUID.class)).thenReturn(TENANT_ID);
        when(rs.getString("tenant_key")).thenReturn("acme-id");
        when(rs.getString("name")).thenReturn("ACME");
        when(rs.getLong("access_token_ttl_seconds")).thenReturn(1800L);
        when(rs.getBoolean("enabled")).thenReturn(true);
        when(rs.getTimestamp("created_at")).thenReturn(Timestamp.from(NOW));
        return rs;
    }

    private static ResultSet userResultSet() throws Exception {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getObject("id", UUID.class)).thenReturn(USER_ID);
        when(rs.getObject("tenant_id", UUID.class)).thenReturn(TENANT_ID);
        when(rs.getString("username")).thenReturn("owner");
        when(rs.getString("email")).thenReturn("owner@example.com");
        when(rs.getString("password_hash")).thenReturn("hash");
        when(rs.getBoolean("enabled")).thenReturn(true);
        when(rs.getTimestamp("created_at")).thenReturn(Timestamp.from(NOW));
        when(rs.getTimestamp("updated_at")).thenReturn(Timestamp.from(NOW));
        return rs;
    }

    private static ResultSet roleResultSet(String resource, String action) throws Exception {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getObject("id", UUID.class)).thenReturn(ROLE_ID);
        when(rs.getObject("tenant_id", UUID.class)).thenReturn(TENANT_ID);
        when(rs.getString("name")).thenReturn("TENANT_OWNER");
        when(rs.getString("description")).thenReturn("owner");
        when(rs.getBoolean("system_role")).thenReturn(true);
        when(rs.getTimestamp("created_at")).thenReturn(Timestamp.from(NOW));
        when(rs.getString("resource")).thenReturn(resource);
        when(rs.getString("action")).thenReturn(action);
        return rs;
    }

    private static ResultSet permissionResultSet() throws Exception {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getObject("id", UUID.class)).thenReturn(PERMISSION_ID);
        when(rs.getObject("tenant_id", UUID.class)).thenReturn(TENANT_ID);
        when(rs.getString("resource")).thenReturn("user");
        when(rs.getString("action")).thenReturn("view");
        when(rs.getString("description")).thenReturn("view users");
        when(rs.getTimestamp("created_at")).thenReturn(Timestamp.from(NOW));
        return rs;
    }
}
