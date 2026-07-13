CREATE TABLE tenants (
  id UUID PRIMARY KEY,
  tenant_key VARCHAR(63) NOT NULL,
  display_name VARCHAR(100) NOT NULL,
  status VARCHAR(32) NOT NULL,
  version BIGINT NOT NULL DEFAULT 0,
  created_at TIMESTAMPTZ NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL,

  CONSTRAINT uk_tenants_tenant_key
    UNIQUE (tenant_key),

  CONSTRAINT ck_tenants_tenant_key_format
    CHECK (tenant_key ~ '^[a-z0-9]+(?:-[a-z0-9]+)*$'),

  CONSTRAINT ck_tenants_display_name
    CHECK (
      display_name = btrim(display_name)
      AND display_name <> ''
    ),

  CONSTRAINT ck_tenants_status
    CHECK (status IN ('ACTIVE', 'SUSPENDED')),

  CONSTRAINT ck_tenants_version
    CHECK (version >= 0)
);
