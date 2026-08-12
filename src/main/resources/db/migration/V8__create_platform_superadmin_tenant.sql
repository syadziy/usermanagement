DO $bootstrap$
DECLARE
    configured_password_hash TEXT := '${defaultSuperadminPasswordHash}';
BEGIN
    IF NOT EXISTS (
           SELECT 1
           FROM user_account
           JOIN tenant ON tenant.id = user_account.tenant_id
           WHERE tenant.tenant_key = 'superadmin'
             AND LOWER(user_account.username) = 'superadmin'
       )
       AND configured_password_hash !~ '^[$]2[aby][$]12[$][./A-Za-z0-9]{53}$' THEN
        RAISE EXCEPTION
            'DEFAULT_SUPERADMIN_PASSWORD_HASH must be a BCrypt strength-12 hash when bootstrapping the superadmin tenant';
    END IF;
END
$bootstrap$;

INSERT INTO tenant (
    id, tenant_key, name, access_token_ttl_seconds, enabled, created_at
)
VALUES (
    gen_random_uuid(), 'superadmin', 'Platform Superadmin', 1800, TRUE, CURRENT_TIMESTAMP
)
ON CONFLICT (tenant_key) DO NOTHING;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM role
        JOIN tenant ON tenant.id = role.tenant_id
        WHERE tenant.tenant_key = 'superadmin'
          AND role.name = 'SUPERADMIN'
          AND role.system_role = FALSE
    ) THEN
        RAISE EXCEPTION 'A non-system SUPERADMIN role already exists in the superadmin tenant';
    END IF;
END
$$;

INSERT INTO permission (id, tenant_id, resource, action, description, created_at)
SELECT gen_random_uuid(), platform_tenant.id, catalog.resource, catalog.action,
       catalog.description, CURRENT_TIMESTAMP
FROM tenant platform_tenant
CROSS JOIN (
    SELECT DISTINCT ON (resource, action) resource, action, description
    FROM permission
    ORDER BY resource, action, created_at
) catalog
WHERE platform_tenant.tenant_key = 'superadmin'
ON CONFLICT (tenant_id, resource, action) DO NOTHING;

INSERT INTO role (id, tenant_id, name, description, system_role, created_at)
SELECT gen_random_uuid(), tenant.id, 'SUPERADMIN', 'Full platform administration access', TRUE,
       CURRENT_TIMESTAMP
FROM tenant
WHERE tenant.tenant_key = 'superadmin'
  AND NOT EXISTS (
      SELECT 1 FROM role
      WHERE role.tenant_id = tenant.id AND role.name = 'SUPERADMIN'
  );

INSERT INTO user_account (
    id, tenant_id, username, email, password_hash, enabled, created_at, updated_at
)
SELECT gen_random_uuid(), tenant.id, 'superadmin', 'superadmin@platform.local',
       '${defaultSuperadminPasswordHash}', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM tenant
WHERE tenant.tenant_key = 'superadmin'
  AND NOT EXISTS (
      SELECT 1 FROM user_account
      WHERE user_account.tenant_id = tenant.id
        AND (LOWER(user_account.username) = 'superadmin'
             OR LOWER(user_account.email) = 'superadmin@platform.local')
  );

INSERT INTO role_permission (tenant_id, role_id, permission_id, assigned_at)
SELECT role.tenant_id, role.id, permission.id, CURRENT_TIMESTAMP
FROM role
JOIN tenant ON tenant.id = role.tenant_id
JOIN permission ON permission.tenant_id = role.tenant_id
WHERE tenant.tenant_key = 'superadmin'
  AND role.name = 'SUPERADMIN'
  AND role.system_role = TRUE
ON CONFLICT (role_id, permission_id) DO NOTHING;

INSERT INTO user_role (tenant_id, user_id, role_id, assigned_at)
SELECT user_account.tenant_id, user_account.id, role.id, CURRENT_TIMESTAMP
FROM user_account
JOIN tenant ON tenant.id = user_account.tenant_id
JOIN role ON role.tenant_id = user_account.tenant_id
WHERE tenant.tenant_key = 'superadmin'
  AND LOWER(user_account.username) = 'superadmin'
  AND role.name = 'SUPERADMIN'
  AND role.system_role = TRUE
ON CONFLICT (user_id, role_id) DO NOTHING;
