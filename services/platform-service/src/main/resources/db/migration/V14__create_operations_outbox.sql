CREATE TABLE operations_event_stream_versions (
  tenant_id UUID NOT NULL,
  aggregate_type VARCHAR(64) NOT NULL,
  aggregate_id UUID NOT NULL,
  last_version BIGINT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL,

  PRIMARY KEY (tenant_id, aggregate_type, aggregate_id),

  CONSTRAINT ck_operations_event_stream_aggregate_type
    CHECK (
      aggregate_type IN (
        'BUSINESS_PARTNER',
        'INVOICE',
        'PAYMENT',
        'RECEIVABLE_SETTLEMENT'
      )
    ),

  CONSTRAINT ck_operations_event_stream_version
    CHECK (last_version > 0),

  CONSTRAINT ck_operations_event_stream_timestamps
    CHECK (updated_at >= created_at)
);

CREATE TABLE operations_outbox_events (
  event_id UUID PRIMARY KEY,
  event_type VARCHAR(128) NOT NULL,
  schema_version INTEGER NOT NULL,
  tenant_id UUID NOT NULL,
  aggregate_type VARCHAR(64) NOT NULL,
  aggregate_id UUID NOT NULL,
  aggregate_version BIGINT NOT NULL,
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

  CONSTRAINT uk_operations_outbox_stream_version
    UNIQUE (tenant_id, aggregate_type, aggregate_id, aggregate_version),

  CONSTRAINT fk_operations_outbox_stream
    FOREIGN KEY (tenant_id, aggregate_type, aggregate_id)
    REFERENCES operations_event_stream_versions (tenant_id, aggregate_type, aggregate_id),

  CONSTRAINT ck_operations_outbox_event_contract
    CHECK (
      (event_type = 'operations.business-partner.synchronized.v1'
        AND aggregate_type = 'BUSINESS_PARTNER')
      OR (event_type = 'operations.invoice.synchronized.v1'
        AND aggregate_type = 'INVOICE')
      OR (event_type = 'operations.payment.synchronized.v1'
        AND aggregate_type = 'PAYMENT')
      OR (event_type = 'operations.receivable-allocation.applied.v1'
        AND aggregate_type = 'RECEIVABLE_SETTLEMENT')
      OR (event_type = 'operations.receivable-allocation.reversed.v1'
        AND aggregate_type = 'RECEIVABLE_SETTLEMENT')
    ),

  CONSTRAINT ck_operations_outbox_schema_version
    CHECK (schema_version = 1),

  CONSTRAINT ck_operations_outbox_aggregate_version
    CHECK (aggregate_version > 0),

  CONSTRAINT ck_operations_outbox_payload
    CHECK (jsonb_typeof(payload) = 'object'),

  CONSTRAINT ck_operations_outbox_publish_status
    CHECK (
      publish_status IN (
        'PENDING',
        'CLAIMED',
        'RETRY_SCHEDULED',
        'PUBLISHED',
        'FAILED'
      )
    ),

  CONSTRAINT ck_operations_outbox_attempt_count
    CHECK (publish_attempt_count >= 0),

  CONSTRAINT ck_operations_outbox_failure_code
    CHECK (
      last_failure_code IS NULL
      OR last_failure_code ~ '^[a-z0-9]+(?:-[a-z0-9]+)*$'
    ),

  CONSTRAINT ck_operations_outbox_timestamps
    CHECK (
      next_publish_at >= occurred_at
      AND created_at >= occurred_at
      AND updated_at >= created_at
      AND (claimed_at IS NULL OR claimed_at >= occurred_at)
      AND (published_at IS NULL OR published_at >= occurred_at)
    ),

  CONSTRAINT ck_operations_outbox_state
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

CREATE INDEX ix_operations_outbox_publishable
  ON operations_outbox_events (next_publish_at, occurred_at, event_id)
  WHERE publish_status IN ('PENDING', 'RETRY_SCHEDULED');

CREATE INDEX ix_operations_outbox_stale_claims
  ON operations_outbox_events (claimed_at, event_id)
  WHERE publish_status = 'CLAIMED';

CREATE INDEX ix_operations_outbox_unpublished_stream
  ON operations_outbox_events (
    tenant_id,
    aggregate_type,
    aggregate_id,
    aggregate_version
  )
  WHERE publish_status <> 'PUBLISHED';

CREATE INDEX ix_operations_outbox_tenant_history
  ON operations_outbox_events (tenant_id, occurred_at DESC, event_id DESC);
