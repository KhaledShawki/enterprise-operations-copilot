ALTER TABLE connector_import_runs
  ADD CONSTRAINT uk_connector_import_runs_id_tenant
  UNIQUE (id, tenant_id);

CREATE TABLE connector_outbox_events (
  event_id UUID PRIMARY KEY,
  event_type VARCHAR(128) NOT NULL,
  schema_version INTEGER NOT NULL,
  tenant_id UUID NOT NULL,
  aggregate_type VARCHAR(64) NOT NULL,
  aggregate_id UUID NOT NULL,
  payload JSONB NOT NULL,
  occurred_at TIMESTAMPTZ NOT NULL,
  publish_status VARCHAR(32) NOT NULL,
  publish_attempt_count INTEGER NOT NULL,
  next_publish_at TIMESTAMPTZ NOT NULL,
  claimed_at TIMESTAMPTZ,
  claimed_by VARCHAR(128),
  published_at TIMESTAMPTZ,
  last_failure_code VARCHAR(128),
  created_at TIMESTAMPTZ NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL,

  CONSTRAINT fk_connector_outbox_import_run
    FOREIGN KEY (aggregate_id, tenant_id)
    REFERENCES connector_import_runs (id, tenant_id),

  CONSTRAINT ck_connector_outbox_event_type
    CHECK (
      event_type IN (
        'connector.import-run.completed.v1',
        'connector.import-run.failed.v1',
        'connector.import-run.retry-scheduled.v1'
      )
    ),

  CONSTRAINT ck_connector_outbox_schema_version
    CHECK (schema_version = 1),

  CONSTRAINT ck_connector_outbox_aggregate_type
    CHECK (aggregate_type = 'IMPORT_RUN'),

  CONSTRAINT ck_connector_outbox_payload
    CHECK (jsonb_typeof(payload) = 'object'),

  CONSTRAINT ck_connector_outbox_publish_status
    CHECK (
      publish_status IN (
        'PENDING',
        'CLAIMED',
        'RETRY_SCHEDULED',
        'PUBLISHED',
        'FAILED'
      )
    ),

  CONSTRAINT ck_connector_outbox_attempt_count
    CHECK (publish_attempt_count >= 0),

  CONSTRAINT ck_connector_outbox_failure_code
    CHECK (
      last_failure_code IS NULL
      OR last_failure_code ~ '^[a-z0-9]+(?:-[a-z0-9]+)*$'
    ),

  CONSTRAINT ck_connector_outbox_timestamps
    CHECK (
      next_publish_at >= occurred_at
      AND created_at >= occurred_at
      AND updated_at >= created_at
      AND (claimed_at IS NULL OR claimed_at >= occurred_at)
      AND (published_at IS NULL OR published_at >= occurred_at)
    ),

  CONSTRAINT ck_connector_outbox_state
    CHECK (
      (
        publish_status = 'PENDING'
        AND publish_attempt_count = 0
        AND claimed_at IS NULL
        AND claimed_by IS NULL
        AND published_at IS NULL
        AND last_failure_code IS NULL
      )
      OR (
        publish_status = 'CLAIMED'
        AND publish_attempt_count > 0
        AND claimed_at IS NOT NULL
        AND claimed_by IS NOT NULL
        AND published_at IS NULL
      )
      OR (
        publish_status = 'RETRY_SCHEDULED'
        AND publish_attempt_count > 0
        AND claimed_at IS NULL
        AND claimed_by IS NULL
        AND published_at IS NULL
        AND last_failure_code IS NOT NULL
      )
      OR (
        publish_status = 'PUBLISHED'
        AND publish_attempt_count > 0
        AND claimed_at IS NULL
        AND claimed_by IS NULL
        AND published_at IS NOT NULL
        AND last_failure_code IS NULL
      )
      OR (
        publish_status = 'FAILED'
        AND publish_attempt_count > 0
        AND claimed_at IS NULL
        AND claimed_by IS NULL
        AND published_at IS NULL
        AND last_failure_code IS NOT NULL
      )
    )
);

CREATE INDEX ix_connector_outbox_publishable
  ON connector_outbox_events (next_publish_at, occurred_at, event_id)
  WHERE publish_status IN ('PENDING', 'RETRY_SCHEDULED');

CREATE INDEX ix_connector_outbox_stale_claims
  ON connector_outbox_events (claimed_at, event_id)
  WHERE publish_status = 'CLAIMED';

CREATE INDEX ix_connector_outbox_tenant_history
  ON connector_outbox_events (tenant_id, occurred_at DESC, event_id DESC);
