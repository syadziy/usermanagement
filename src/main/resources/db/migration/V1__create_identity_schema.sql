CREATE TABLE tenant (
    id UUID PRIMARY KEY,
    tenant_key VARCHAR(64) NOT NULL UNIQUE,
    name VARCHAR(150) NOT NULL,
    access_token_ttl_seconds INTEGER NOT NULL CHECK (access_token_ttl_seconds BETWEEN 60 AND 86400),
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE user_account (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenant(id),
    username VARCHAR(80) NOT NULL,
    email VARCHAR(254) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_user_tenant_username UNIQUE (tenant_id, username),
    CONSTRAINT uq_user_tenant_email UNIQUE (tenant_id, email),
    CONSTRAINT uq_user_id_tenant UNIQUE (id, tenant_id)
);

CREATE TABLE role (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenant(id),
    name VARCHAR(80) NOT NULL,
    description VARCHAR(255),
    system_role BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_role_tenant_name UNIQUE (tenant_id, name),
    CONSTRAINT uq_role_id_tenant UNIQUE (id, tenant_id)
);

CREATE TABLE permission (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenant(id),
    resource VARCHAR(80) NOT NULL,
    action VARCHAR(80) NOT NULL,
    description VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_permission_tenant_resource_action UNIQUE (tenant_id, resource, action),
    CONSTRAINT uq_permission_id_tenant UNIQUE (id, tenant_id)
);

CREATE TABLE user_role (
    tenant_id UUID NOT NULL REFERENCES tenant(id),
    user_id UUID NOT NULL,
    role_id UUID NOT NULL,
    assigned_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (user_id, role_id),
    FOREIGN KEY (user_id, tenant_id) REFERENCES user_account(id, tenant_id) ON DELETE CASCADE,
    FOREIGN KEY (role_id, tenant_id) REFERENCES role(id, tenant_id)
);

CREATE TABLE role_permission (
    tenant_id UUID NOT NULL REFERENCES tenant(id),
    role_id UUID NOT NULL,
    permission_id UUID NOT NULL,
    assigned_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (role_id, permission_id),
    FOREIGN KEY (role_id, tenant_id) REFERENCES role(id, tenant_id) ON DELETE CASCADE,
    FOREIGN KEY (permission_id, tenant_id) REFERENCES permission(id, tenant_id) ON DELETE CASCADE
);

CREATE INDEX idx_user_tenant_enabled ON user_account (tenant_id, enabled);
CREATE INDEX idx_role_tenant ON role (tenant_id);
CREATE INDEX idx_permission_tenant ON permission (tenant_id);
CREATE INDEX idx_user_role_tenant_user ON user_role (tenant_id, user_id);
CREATE INDEX idx_role_permission_tenant_role ON role_permission (tenant_id, role_id);
