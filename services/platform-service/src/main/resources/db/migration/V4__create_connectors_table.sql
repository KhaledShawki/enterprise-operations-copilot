CREATE TABLE connectors (
  id UUID PRIMARY KEY,
  tenant_id UUID NOT NULL,
  name VARCHAR(100) NOT NULL,
  connector_type VARCHAR(63) NOT NULL,
  status VARCHAR(32) NOT NULL,
  endpoint VARCHAR(2048) NOT NULL,
  credential_reference UUID NOT NULL,
  sync_mode VARCHAR(32) NOT NULL,
  sync_interval VARCHAR(64) NOT NULL,
  health VARCHAR(32) NOT NULL,
  version BIGINT NOT NULL DEFAULT 0,
  created_at TIMESTAMPTZ NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL,

  CONSTRAINT uk_connectors_tenant_name
    UNIQUE (tenant_id, name),

  CONSTRAINT ck_connectors_name
    CHECK (
      name = btrim(name)
      AND name <> ''
    ),

  CONSTRAINT ck_connectors_type_format
    CHECK (connector_type ~ '^[a-z0-9]+(?:-[a-z0-9]+)*$'),

  CONSTRAINT ck_connectors_status
    CHECK (status IN ('DRAFT', 'ACTIVE', 'SUSPENDED')),

  CONSTRAINT ck_connectors_endpoint
    CHECK (
      endpoint = btrim(endpoint)
      AND endpoint ~* '^https?://'
    ),

  CONSTRAINT ck_connectors_sync_policy
    CHECK (
      (sync_mode = 'MANUAL' AND sync_interval = 'PT0S')
      OR (sync_mode = 'SCHEDULED' AND sync_interval <> 'PT0S')
    ),

  CONSTRAINT ck_connectors_health
    CHECK (health IN ('UNKNOWN', 'HEALTHY', 'UNHEALTHY')),

  CONSTRAINT ck_connectors_version
    CHECK (version >= 0)
);

CREATE INDEX ix_connectors_tenant_id
  ON connectors (tenant_id);
