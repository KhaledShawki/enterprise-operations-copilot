CREATE TABLE platform_users (
  id UUID PRIMARY KEY,
  issuer VARCHAR(2048) NOT NULL,
  subject VARCHAR(255) NOT NULL,
  status VARCHAR(32) NOT NULL,
  version BIGINT NOT NULL DEFAULT 0,
  created_at TIMESTAMPTZ NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL,

  CONSTRAINT uk_platform_users_external_identity
    UNIQUE (issuer, subject),

  CONSTRAINT ck_platform_users_issuer
    CHECK (btrim(issuer) <> ''),

  CONSTRAINT ck_platform_users_subject
    CHECK (btrim(subject) <> ''),

  CONSTRAINT ck_platform_users_status
    CHECK (status IN ('ACTIVE', 'SUSPENDED')),

  CONSTRAINT ck_platform_users_version
    CHECK (version >= 0)
);

CREATE TABLE tenant_memberships (
  id UUID PRIMARY KEY,
  tenant_id UUID NOT NULL,
  platform_user_id UUID NOT NULL,
  status VARCHAR(32) NOT NULL,
  version BIGINT NOT NULL DEFAULT 0,
  created_at TIMESTAMPTZ NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL,

  CONSTRAINT uk_tenant_memberships_tenant_user
    UNIQUE (tenant_id, platform_user_id),

  CONSTRAINT fk_tenant_memberships_tenant
    FOREIGN KEY (tenant_id)
      REFERENCES tenants (id)
      ON DELETE RESTRICT,

  CONSTRAINT fk_tenant_memberships_platform_user
    FOREIGN KEY (platform_user_id)
      REFERENCES platform_users (id)
      ON DELETE RESTRICT,

  CONSTRAINT ck_tenant_memberships_status
    CHECK (status IN ('ACTIVE', 'SUSPENDED')),

  CONSTRAINT ck_tenant_memberships_version
    CHECK (version >= 0)
);

CREATE INDEX ix_tenant_memberships_platform_user_id
  ON tenant_memberships (platform_user_id);
