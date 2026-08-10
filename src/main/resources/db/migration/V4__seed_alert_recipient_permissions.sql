WITH recipient_permission(resource, action, description) AS (
    VALUES
        ('alert', 'read-recipients', 'Allows reading centralized alert recipients'),
        ('alert', 'manage-recipients', 'Allows managing centralized alert recipients')
)
INSERT INTO permission (id, tenant_id, resource, action, description, created_at)
SELECT gen_random_uuid(), tenant.id, recipient_permission.resource, recipient_permission.action,
       recipient_permission.description, CURRENT_TIMESTAMP
FROM tenant
CROSS JOIN recipient_permission
ON CONFLICT (tenant_id, resource, action) DO NOTHING;

INSERT INTO role_permission (tenant_id, role_id, permission_id, assigned_at)
SELECT role.tenant_id, role.id, permission.id, CURRENT_TIMESTAMP
FROM role
JOIN permission ON permission.tenant_id = role.tenant_id
WHERE role.name = 'TENANT_OWNER'
  AND permission.resource = 'alert'
  AND permission.action IN ('read-recipients', 'manage-recipients')
ON CONFLICT (role_id, permission_id) DO NOTHING;
