DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM role
        WHERE name = 'SUPERADMIN'
          AND system_role = FALSE
    ) THEN
        RAISE EXCEPTION 'A non-system SUPERADMIN role already exists';
    END IF;
END
$$;

INSERT INTO role (id, tenant_id, name, description, system_role, created_at)
SELECT gen_random_uuid(), tenant.id, 'SUPERADMIN', 'Full tenant administration access', TRUE,
       CURRENT_TIMESTAMP
FROM tenant
WHERE NOT EXISTS (
    SELECT 1
    FROM role
    WHERE role.tenant_id = tenant.id
      AND role.name = 'SUPERADMIN'
);

INSERT INTO role_permission (tenant_id, role_id, permission_id, assigned_at)
SELECT superadmin.tenant_id, superadmin.id, permission.id, CURRENT_TIMESTAMP
FROM role superadmin
JOIN permission ON permission.tenant_id = superadmin.tenant_id
WHERE superadmin.name = 'SUPERADMIN'
  AND superadmin.system_role = TRUE
ON CONFLICT (role_id, permission_id) DO NOTHING;

INSERT INTO user_role (tenant_id, user_id, role_id, assigned_at)
SELECT owner_assignment.tenant_id, owner_assignment.user_id, superadmin.id, CURRENT_TIMESTAMP
FROM user_role owner_assignment
JOIN role owner_role
  ON owner_role.id = owner_assignment.role_id
 AND owner_role.tenant_id = owner_assignment.tenant_id
JOIN role superadmin
  ON superadmin.tenant_id = owner_assignment.tenant_id
 AND superadmin.name = 'SUPERADMIN'
 AND superadmin.system_role = TRUE
WHERE owner_role.name = 'TENANT_OWNER'
  AND owner_role.system_role = TRUE
ON CONFLICT (user_id, role_id) DO NOTHING;
