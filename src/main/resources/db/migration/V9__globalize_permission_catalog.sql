CREATE TABLE permission_global (
    id UUID PRIMARY KEY,
    resource VARCHAR(80) NOT NULL,
    action VARCHAR(80) NOT NULL,
    description VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_permission_global_resource_action UNIQUE (resource, action)
);

INSERT INTO permission_global (id, resource, action, description, created_at)
SELECT DISTINCT ON (resource, action)
       id, resource, action, description, created_at
FROM permission
ORDER BY resource, action, created_at, id;

CREATE TABLE role_permission_global (
    tenant_id UUID NOT NULL REFERENCES tenant(id),
    role_id UUID NOT NULL,
    permission_id UUID NOT NULL,
    assigned_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_role_permission_global PRIMARY KEY (role_id, permission_id),
    CONSTRAINT fk_role_permission_global_role
        FOREIGN KEY (role_id, tenant_id) REFERENCES role(id, tenant_id) ON DELETE CASCADE,
    CONSTRAINT fk_role_permission_global_permission
        FOREIGN KEY (permission_id) REFERENCES permission_global(id) ON DELETE CASCADE
);

INSERT INTO role_permission_global (tenant_id, role_id, permission_id, assigned_at)
SELECT rp.tenant_id, rp.role_id, global_permission.id, MIN(rp.assigned_at)
FROM role_permission rp
JOIN permission tenant_permission
  ON tenant_permission.id = rp.permission_id
 AND tenant_permission.tenant_id = rp.tenant_id
JOIN permission_global global_permission
  ON global_permission.resource = tenant_permission.resource
 AND global_permission.action = tenant_permission.action
GROUP BY rp.tenant_id, rp.role_id, global_permission.id;

INSERT INTO role_permission_global (tenant_id, role_id, permission_id, assigned_at)
SELECT role.tenant_id, role.id, global_permission.id, CURRENT_TIMESTAMP
FROM role
CROSS JOIN permission_global global_permission
WHERE role.name = 'SUPERADMIN'
  AND role.system_role = TRUE
ON CONFLICT (role_id, permission_id) DO NOTHING;

DROP TABLE role_permission;
DROP TABLE permission;

ALTER TABLE permission_global RENAME TO permission;
ALTER TABLE role_permission_global RENAME TO role_permission;

CREATE INDEX idx_role_permission_tenant_role ON role_permission (tenant_id, role_id);
