WITH notification_permission(resource, action, description) AS (
    VALUES ('alert', 'read-notifications', 'Allows reading realtime centralized alert notifications')
)
INSERT INTO permission (id, tenant_id, resource, action, description, created_at)
SELECT gen_random_uuid(), tenant.id, notification_permission.resource, notification_permission.action,
       notification_permission.description, CURRENT_TIMESTAMP
FROM tenant
CROSS JOIN notification_permission
ON CONFLICT (tenant_id, resource, action) DO NOTHING;

INSERT INTO role_permission (tenant_id, role_id, permission_id, assigned_at)
SELECT role.tenant_id, role.id, permission.id, CURRENT_TIMESTAMP
FROM role
JOIN permission ON permission.tenant_id = role.tenant_id
WHERE role.name = 'TENANT_OWNER'
  AND permission.resource = 'alert'
  AND permission.action = 'read-notifications'
ON CONFLICT (role_id, permission_id) DO NOTHING;
