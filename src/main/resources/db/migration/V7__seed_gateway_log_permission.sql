WITH gateway_log_permission(resource, action, description) AS (
    VALUES ('gateway-log', 'read', 'Allows reading API Gateway request logs')
)
INSERT INTO permission (id, tenant_id, resource, action, description, created_at)
SELECT gen_random_uuid(), tenant.id, gateway_log_permission.resource, gateway_log_permission.action,
       gateway_log_permission.description, CURRENT_TIMESTAMP
FROM tenant
CROSS JOIN gateway_log_permission
ON CONFLICT (tenant_id, resource, action) DO NOTHING;

INSERT INTO role_permission (tenant_id, role_id, permission_id, assigned_at)
SELECT role.tenant_id, role.id, permission.id, CURRENT_TIMESTAMP
FROM role
JOIN permission ON permission.tenant_id = role.tenant_id
WHERE role.name IN ('TENANT_OWNER', 'SUPERADMIN')
  AND role.system_role = TRUE
  AND permission.resource = 'gateway-log'
  AND permission.action = 'read'
ON CONFLICT (role_id, permission_id) DO NOTHING;
