CREATE TABLE connector_inbox_events (
  event_id UUID PRIMARY KEY,
  event_type VARCHAR(128) NOT NULL,
  schema_version INTEGER NOT NULL,
  tenant_id UUID NOT NULL,
  aggregate_type VARCHAR(64) NOT NULL,
  aggregate_id UUID NOT NULL,
  payload JSONB NOT NULL,
  payload_fingerprint VARCHAR(64) NOT NULL,
  occurred_at TIMESTAMPTZ NOT NULL,
  received_at TIMESTAMPTZ NOT NULL,
  processed_at TIMESTAMPTZ NOT NULL,

  CONSTRAINT ck_connector_inbox_event_type
    CHECK (
      event_type IN (
        'connector.import-run.completed.v1',
        'connector.import-run.failed.v1',
        'connector.import-run.retry-scheduled.v1'
      )
    ),

  CONSTRAINT ck_connector_inbox_schema_version
    CHECK (schema_version = 1),

  CONSTRAINT ck_connector_inbox_aggregate_type
    CHECK (aggregate_type = 'IMPORT_RUN'),

  CONSTRAINT ck_connector_inbox_payload
    CHECK (jsonb_typeof(payload) = 'object'),

  CONSTRAINT ck_connector_inbox_fingerprint
    CHECK (payload_fingerprint ~ '^[0-9a-f]{64}$'),

  CONSTRAINT ck_connector_inbox_timestamps
    CHECK (processed_at >= received_at)
);

CREATE INDEX ix_connector_inbox_tenant_history
  ON connector_inbox_events (tenant_id, occurred_at DESC, event_id DESC);

CREATE INDEX ix_connector_inbox_aggregate_history
  ON connector_inbox_events (tenant_id, aggregate_type, aggregate_id, occurred_at, event_id);

CREATE TABLE connector_import_run_event_projection (
  event_id UUID PRIMARY KEY,
  event_type VARCHAR(128) NOT NULL,
  schema_version INTEGER NOT NULL,
  tenant_id UUID NOT NULL,
  import_run_id UUID NOT NULL,
  payload JSONB NOT NULL,
  occurred_at TIMESTAMPTZ NOT NULL,
  projected_at TIMESTAMPTZ NOT NULL,

  CONSTRAINT fk_connector_import_run_event_projection_inbox
    FOREIGN KEY (event_id)
    REFERENCES connector_inbox_events (event_id),

  CONSTRAINT ck_connector_import_run_projection_event_type
    CHECK (
      event_type IN (
        'connector.import-run.completed.v1',
        'connector.import-run.failed.v1',
        'connector.import-run.retry-scheduled.v1'
      )
    ),

  CONSTRAINT ck_connector_import_run_projection_schema_version
    CHECK (schema_version = 1),

  CONSTRAINT ck_connector_import_run_projection_payload
    CHECK (jsonb_typeof(payload) = 'object')
);

CREATE INDEX ix_connector_import_run_event_projection_history
  ON connector_import_run_event_projection (
    tenant_id,
    import_run_id,
    occurred_at,
    event_id
  );
