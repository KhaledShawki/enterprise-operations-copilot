CREATE TABLE connector_dead_letter_replay_requests (
  replay_request_id UUID PRIMARY KEY,
  dlt_topic VARCHAR(249) NOT NULL,
  dlt_partition INTEGER NOT NULL,
  dlt_offset BIGINT NOT NULL,
  record_fingerprint VARCHAR(64) NOT NULL,
  source_topic VARCHAR(249) NOT NULL,
  source_partition INTEGER NOT NULL,
  source_offset BIGINT NOT NULL,
  source_timestamp TIMESTAMPTZ NOT NULL,
  record_key TEXT,
  record_value TEXT,
  replay_headers JSONB NOT NULL,
  replay_generation INTEGER NOT NULL,
  requested_by_issuer VARCHAR(500) NOT NULL,
  requested_by_subject VARCHAR(255) NOT NULL,
  request_reason VARCHAR(500) NOT NULL,
  replay_status VARCHAR(32) NOT NULL,
  publication_attempt_count INTEGER NOT NULL,
  next_attempt_at TIMESTAMPTZ NOT NULL,
  claimed_at TIMESTAMPTZ,
  claimed_by VARCHAR(128),
  last_failure_code VARCHAR(160),
  requested_at TIMESTAMPTZ NOT NULL,
  replayed_at TIMESTAMPTZ,
  updated_at TIMESTAMPTZ NOT NULL,

  CONSTRAINT uk_connector_dead_letter_replay_coordinates
    UNIQUE (dlt_topic, dlt_partition, dlt_offset),

  CONSTRAINT ck_connector_dead_letter_replay_coordinates
    CHECK (
      dlt_partition >= 0
      AND dlt_offset >= 0
      AND source_partition >= 0
      AND source_offset >= 0
    ),

  CONSTRAINT ck_connector_dead_letter_replay_fingerprint
    CHECK (record_fingerprint ~ '^[0-9a-f]{64}$'),

  CONSTRAINT ck_connector_dead_letter_replay_headers
    CHECK (jsonb_typeof(replay_headers) = 'array'),

  CONSTRAINT ck_connector_dead_letter_replay_generation
    CHECK (replay_generation BETWEEN 1 AND 100),

  CONSTRAINT ck_connector_dead_letter_replay_status
    CHECK (
      replay_status IN (
        'PENDING',
        'CLAIMED',
        'RETRY_SCHEDULED',
        'REPLAYED',
        'FAILED'
      )
    ),

  CONSTRAINT ck_connector_dead_letter_replay_attempt_count
    CHECK (publication_attempt_count >= 0),

  CONSTRAINT ck_connector_dead_letter_replay_failure_code
    CHECK (
      last_failure_code IS NULL
      OR last_failure_code ~ '^[a-z0-9]+(?:-[a-z0-9]+)*$'
    ),

  CONSTRAINT ck_connector_dead_letter_replay_timestamps
    CHECK (
      next_attempt_at >= requested_at
      AND updated_at >= requested_at
      AND (claimed_at IS NULL OR claimed_at >= requested_at)
      AND (replayed_at IS NULL OR replayed_at >= requested_at)
    ),

  CONSTRAINT ck_connector_dead_letter_replay_state
    CHECK (
      (
        replay_status = 'PENDING'
        AND publication_attempt_count = 0
        AND claimed_at IS NULL
        AND claimed_by IS NULL
        AND last_failure_code IS NULL
        AND replayed_at IS NULL
      )
      OR (
        replay_status = 'CLAIMED'
        AND publication_attempt_count > 0
        AND claimed_at IS NOT NULL
        AND claimed_by IS NOT NULL
        AND replayed_at IS NULL
      )
      OR (
        replay_status = 'RETRY_SCHEDULED'
        AND publication_attempt_count > 0
        AND claimed_at IS NULL
        AND claimed_by IS NULL
        AND last_failure_code IS NOT NULL
        AND replayed_at IS NULL
      )
      OR (
        replay_status = 'REPLAYED'
        AND publication_attempt_count > 0
        AND claimed_at IS NULL
        AND claimed_by IS NULL
        AND last_failure_code IS NULL
        AND replayed_at IS NOT NULL
      )
      OR (
        replay_status = 'FAILED'
        AND publication_attempt_count > 0
        AND claimed_at IS NULL
        AND claimed_by IS NULL
        AND last_failure_code IS NOT NULL
        AND replayed_at IS NULL
      )
    )
);

CREATE INDEX ix_connector_dead_letter_replay_publishable
  ON connector_dead_letter_replay_requests (next_attempt_at, requested_at, replay_request_id)
  WHERE replay_status IN ('PENDING', 'RETRY_SCHEDULED');

CREATE INDEX ix_connector_dead_letter_replay_stale_claims
  ON connector_dead_letter_replay_requests (claimed_at, replay_request_id)
  WHERE replay_status = 'CLAIMED';

CREATE INDEX ix_connector_dead_letter_replay_history
  ON connector_dead_letter_replay_requests (requested_at DESC, replay_request_id DESC);
