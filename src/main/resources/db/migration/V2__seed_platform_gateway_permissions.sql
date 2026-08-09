WITH platform_permission(resource, action, description) AS (
    VALUES
        ('alert', 'write', 'Allows write on alert'),
        ('audit', 'read', 'Allows read on audit'),
        ('scheduler', 'read', 'Allows read on scheduler'),
        ('scheduler', 'manage', 'Allows manage on scheduler')
)
INSERT INTO permission (id, tenant_id, resource, action, description, created_at)
SELECT gen_random_uuid(), tenant.id, platform_permission.resource, platform_permission.action,
       platform_permission.description, CURRENT_TIMESTAMP
FROM tenant
CROSS JOIN platform_permission
ON CONFLICT (tenant_id, resource, action) DO NOTHING;

INSERT INTO role_permission (tenant_id, role_id, permission_id, assigned_at)
SELECT role.tenant_id, role.id, permission.id, CURRENT_TIMESTAMP
FROM role
JOIN permission ON permission.tenant_id = role.tenant_id
WHERE role.name = 'TENANT_OWNER'
  AND (permission.resource, permission.action) IN (
      ('alert', 'write'),
      ('audit', 'read'),
      ('scheduler', 'read'),
      ('scheduler', 'manage')
  )
ON CONFLICT (role_id, permission_id) DO NOTHING;
