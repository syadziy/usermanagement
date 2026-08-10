DO $bootstrap$
DECLARE
    configured_password_hash TEXT := '${defaultSuperadminPasswordHash}';
BEGIN
    IF NOT EXISTS (SELECT 1 FROM tenant WHERE tenant_key = 'syadziy-company')
       AND configured_password_hash !~ '^[$]2[aby][$]12[$][./A-Za-z0-9]{53}$' THEN
        RAISE EXCEPTION
            'DEFAULT_SUPERADMIN_PASSWORD_HASH must be a BCrypt strength-12 hash when bootstrapping syadziy-company';
    END IF;
END
$bootstrap$;

INSERT INTO tenant (
    id, tenant_key, name, access_token_ttl_seconds, enabled, created_at
)
VALUES (
    gen_random_uuid(), 'syadziy-company', 'Syadziy Company', 1800, TRUE, CURRENT_TIMESTAMP
)
ON CONFLICT (tenant_key) DO NOTHING;

WITH default_permission(resource, action) AS (
    VALUES
        ('tenant', 'view'),
        ('tenant', 'update'),
        ('user', 'view'),
        ('user', 'create'),
        ('user', 'edit'),
        ('user', 'delete'),
        ('user', 'download'),
        ('user', 'upload'),
        ('role', 'view'),
        ('role', 'create'),
        ('role', 'edit'),
        ('role', 'delete'),
        ('role', 'assign'),
        ('permission', 'view'),
        ('permission', 'create'),
        ('alert', 'write'),
        ('alert', 'read-recipients'),
        ('alert', 'manage-recipients'),
        ('alert', 'read-notifications'),
        ('audit', 'read'),
        ('scheduler', 'read'),
        ('scheduler', 'manage')
)
INSERT INTO permission (id, tenant_id, resource, action, description, created_at)
SELECT gen_random_uuid(), tenant.id, default_permission.resource, default_permission.action,
       'Allows ' || default_permission.action || ' on ' || default_permission.resource,
       CURRENT_TIMESTAMP
FROM tenant
CROSS JOIN default_permission
WHERE tenant.tenant_key = 'syadziy-company'
ON CONFLICT (tenant_id, resource, action) DO NOTHING;

INSERT INTO role (id, tenant_id, name, description, system_role, created_at)
SELECT gen_random_uuid(), tenant.id, 'SUPERADMIN', 'Full tenant administration access', TRUE,
       CURRENT_TIMESTAMP
FROM tenant
WHERE tenant.tenant_key = 'syadziy-company'
  AND NOT EXISTS (
      SELECT 1
      FROM role
      WHERE role.tenant_id = tenant.id
        AND role.name = 'SUPERADMIN'
  );

INSERT INTO user_account (
    id, tenant_id, username, email, password_hash, enabled, created_at, updated_at
)
SELECT gen_random_uuid(), tenant.id, 'syadziy.owner', 'owner@syadziy.company',
       '${defaultSuperadminPasswordHash}', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM tenant
WHERE tenant.tenant_key = 'syadziy-company'
  AND NOT EXISTS (
      SELECT 1
      FROM user_account
      WHERE user_account.tenant_id = tenant.id
        AND (LOWER(user_account.username) = 'syadziy.owner'
             OR LOWER(user_account.email) = 'owner@syadziy.company')
  );

INSERT INTO role_permission (tenant_id, role_id, permission_id, assigned_at)
SELECT role.tenant_id, role.id, permission.id, CURRENT_TIMESTAMP
FROM role
JOIN tenant ON tenant.id = role.tenant_id
JOIN permission ON permission.tenant_id = role.tenant_id
WHERE tenant.tenant_key = 'syadziy-company'
  AND role.name = 'SUPERADMIN'
  AND role.system_role = TRUE
ON CONFLICT (role_id, permission_id) DO NOTHING;

INSERT INTO user_role (tenant_id, user_id, role_id, assigned_at)
SELECT user_account.tenant_id, user_account.id, role.id, CURRENT_TIMESTAMP
FROM user_account
JOIN tenant ON tenant.id = user_account.tenant_id
JOIN role ON role.tenant_id = user_account.tenant_id
WHERE tenant.tenant_key = 'syadziy-company'
  AND LOWER(user_account.username) = 'syadziy.owner'
  AND LOWER(user_account.email) = 'owner@syadziy.company'
  AND role.name = 'SUPERADMIN'
  AND role.system_role = TRUE
ON CONFLICT (user_id, role_id) DO NOTHING;
