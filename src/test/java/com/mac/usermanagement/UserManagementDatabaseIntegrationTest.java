package com.mac.usermanagement;

import static org.assertj.core.api.Assertions.assertThat;

import javax.sql.DataSource;
import java.util.Map;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = {
    "usermanagement.registration.enabled=false",
    "usermanagement.jwt.generate-ephemeral-key=true"
})
class UserManagementDatabaseIntegrationTest {

    private static final String MIGRATION_TEST_SCHEMA = "superadmin_migration_test";
    private static final String TEST_PASSWORD_HASH =
            "$2y$12$Sb.Wzw9Jhd3Z7u6JF70vjuHzDTppkyxYWQ1hyM8i5m1WaKBTp9Q7e";

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.flyway.placeholders.defaultSuperadminPasswordHash", () -> TEST_PASSWORD_HASH);
    }

    @Autowired DataSource dataSource;

    @Test
    void flywayCreatesAllIdentityTables() {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM information_schema.tables
                WHERE table_schema = 'public'
                  AND table_name IN ('tenant', 'user_account', 'role', 'permission', 'user_role', 'role_permission')
                """, Integer.class);
        assertThat(count).isEqualTo(6);
        Boolean bootstrapMigrationApplied = jdbcTemplate.queryForObject("""
                SELECT success FROM flyway_schema_history WHERE version = '6'
                """, Boolean.class);
        assertThat(bootstrapMigrationApplied).isTrue();

        Map<String, Object> bootstrap = jdbcTemplate.queryForMap("""
                SELECT tenant.tenant_key,
                       COUNT(DISTINCT permission.id) AS permission_count,
                       COUNT(DISTINCT role.id) AS role_count,
                       COUNT(DISTINCT user_account.id) AS user_count
                FROM tenant
                LEFT JOIN permission ON permission.tenant_id = tenant.id
                LEFT JOIN role ON role.tenant_id = tenant.id
                LEFT JOIN user_account ON user_account.tenant_id = tenant.id
                WHERE tenant.tenant_key = 'syadziy-company'
                GROUP BY tenant.id, tenant.tenant_key
                """);
        assertThat(bootstrap.get("tenant_key")).isEqualTo("syadziy-company");
        assertThat(((Number) bootstrap.get("permission_count")).intValue()).isEqualTo(22);
        assertThat(((Number) bootstrap.get("role_count")).intValue()).isEqualTo(1);
        assertThat(((Number) bootstrap.get("user_count")).intValue()).isEqualTo(1);
    }

    @Test
    void flywayPromotesPrimaryTenantOwnerToSuperadmin() {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.execute("CREATE SCHEMA " + MIGRATION_TEST_SCHEMA);
        Flyway.configure().dataSource(dataSource).defaultSchema(MIGRATION_TEST_SCHEMA)
                .schemas(MIGRATION_TEST_SCHEMA).locations("classpath:db/migration").target("4").load().migrate();

        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID ownerRoleId = UUID.randomUUID();
        UUID permissionId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO superadmin_migration_test.tenant
                    (id, tenant_key, name, access_token_ttl_seconds, enabled, created_at)
                VALUES (?, 'primary-tenant', 'Primary tenant', 1800, TRUE, CURRENT_TIMESTAMP)
                """, tenantId);
        jdbcTemplate.update("""
                INSERT INTO superadmin_migration_test.user_account
                    (id, tenant_id, username, email, password_hash, enabled, created_at, updated_at)
                VALUES (?, ?, 'primary.user', 'primary@example.com', 'bcrypt-hash', TRUE,
                        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, userId, tenantId);
        jdbcTemplate.update("""
                INSERT INTO superadmin_migration_test.role
                    (id, tenant_id, name, description, system_role, created_at)
                VALUES (?, ?, 'TENANT_OWNER', 'Legacy owner', TRUE, CURRENT_TIMESTAMP)
                """, ownerRoleId, tenantId);
        jdbcTemplate.update("""
                INSERT INTO superadmin_migration_test.permission
                    (id, tenant_id, resource, action, description, created_at)
                VALUES (?, ?, 'tenant', 'update', 'Allows tenant update', CURRENT_TIMESTAMP)
                """, permissionId, tenantId);
        jdbcTemplate.update("""
                INSERT INTO superadmin_migration_test.user_role
                    (tenant_id, user_id, role_id, assigned_at)
                VALUES (?, ?, ?, CURRENT_TIMESTAMP)
                """, tenantId, userId, ownerRoleId);

        Flyway.configure().dataSource(dataSource).defaultSchema(MIGRATION_TEST_SCHEMA)
                .schemas(MIGRATION_TEST_SCHEMA).locations("classpath:db/migration")
                .placeholders(Map.of("defaultSuperadminPasswordHash", TEST_PASSWORD_HASH))
                .load().migrate();

        Integer assignedRoles = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM superadmin_migration_test.user_role user_role
                JOIN superadmin_migration_test.role role ON role.id = user_role.role_id
                WHERE user_role.user_id = ?
                  AND role.name = 'SUPERADMIN'
                  AND role.system_role = TRUE
                """, Integer.class, userId);
        Integer assignedPermissions = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM superadmin_migration_test.role_permission role_permission
                JOIN superadmin_migration_test.role role ON role.id = role_permission.role_id
                WHERE role.tenant_id = ? AND role.name = 'SUPERADMIN'
                """, Integer.class, tenantId);
        assertThat(assignedRoles).isEqualTo(1);
        assertThat(assignedPermissions).isEqualTo(1);
    }
}
