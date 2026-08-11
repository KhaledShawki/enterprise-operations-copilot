ALTER TABLE operations_outbox_events
  ADD COLUMN recovery_generation INTEGER NOT NULL DEFAULT 0,
  ADD COLUMN generation_attempt_count INTEGER;

UPDATE operations_outbox_events
SET generation_attempt_count = publish_attempt_count;

ALTER TABLE operations_outbox_events
  ALTER COLUMN generation_attempt_count SET DEFAULT 0,
  ALTER COLUMN generation_attempt_count SET NOT NULL,
  ADD CONSTRAINT ck_operations_outbox_recovery_generation
    CHECK (recovery_generation >= 0),
  ADD CONSTRAINT ck_operations_outbox_generation_attempt_count
    CHECK (
      generation_attempt_count >= 0
      AND generation_attempt_count <= publish_attempt_count
    );

CREATE TABLE operations_outbox_recoveries (
  recovery_id UUID PRIMARY KEY,
  event_id UUID NOT NULL,
  recovery_generation INTEGER NOT NULL,
  requested_by_issuer VARCHAR(512) NOT NULL,
  requested_by_subject VARCHAR(512) NOT NULL,
  reason VARCHAR(500) NOT NULL,
  previous_status VARCHAR(32) NOT NULL,
  previous_publish_attempt_count INTEGER NOT NULL,
  previous_generation_attempt_count INTEGER NOT NULL,
  previous_failure_code VARCHAR(128) NOT NULL,
  requested_at TIMESTAMPTZ NOT NULL,
  completed_at TIMESTAMPTZ NOT NULL,

  CONSTRAINT fk_operations_outbox_recovery_event
    FOREIGN KEY (event_id)
    REFERENCES operations_outbox_events (event_id),

  CONSTRAINT uk_operations_outbox_recovery_generation
    UNIQUE (event_id, recovery_generation),

  CONSTRAINT ck_operations_outbox_recovery_generation_positive
    CHECK (recovery_generation > 0),

  CONSTRAINT ck_operations_outbox_recovery_actor
    CHECK (
      btrim(requested_by_issuer) <> ''
      AND btrim(requested_by_subject) <> ''
    ),

  CONSTRAINT ck_operations_outbox_recovery_reason
    CHECK (btrim(reason) <> ''),

  CONSTRAINT ck_operations_outbox_recovery_previous_status
    CHECK (previous_status = 'FAILED'),

  CONSTRAINT ck_operations_outbox_recovery_previous_attempts
    CHECK (
      previous_publish_attempt_count > 0
      AND previous_generation_attempt_count > 0
      AND previous_generation_attempt_count <= previous_publish_attempt_count
    ),

  CONSTRAINT ck_operations_outbox_recovery_failure_code
    CHECK (previous_failure_code ~ '^[a-z0-9]+(?:-[a-z0-9]+)*$'),

  CONSTRAINT ck_operations_outbox_recovery_timestamps
    CHECK (completed_at >= requested_at)
);

CREATE INDEX ix_operations_outbox_admin_feed
  ON operations_outbox_events (created_at DESC, event_id DESC);

CREATE INDEX ix_operations_outbox_admin_status_feed
  ON operations_outbox_events (publish_status, created_at DESC, event_id DESC);

CREATE INDEX ix_operations_outbox_recovery_history
  ON operations_outbox_recoveries (event_id, recovery_generation DESC);
