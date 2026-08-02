ALTER TABLE connectors
  ADD CONSTRAINT uk_connectors_id_tenant
  UNIQUE (id, tenant_id);

CREATE TABLE connector_import_runs (
  id UUID PRIMARY KEY,
  tenant_id UUID NOT NULL,
  connector_id UUID NOT NULL,
  import_type VARCHAR(32) NOT NULL,
  import_mode VARCHAR(32) NOT NULL,
  status VARCHAR(32) NOT NULL,
  committed_cursor VARCHAR(2048),
  fetched_count BIGINT NOT NULL DEFAULT 0,
  accepted_count BIGINT NOT NULL DEFAULT 0,
  rejected_count BIGINT NOT NULL DEFAULT 0,
  duplicate_count BIGINT NOT NULL DEFAULT 0,
  failure_category VARCHAR(64),
  failure_code VARCHAR(63),
  attempt_count INTEGER NOT NULL DEFAULT 0,
  requested_at TIMESTAMPTZ NOT NULL,
  started_at TIMESTAMPTZ,
  finished_at TIMESTAMPTZ,
  next_retry_at TIMESTAMPTZ,
  version BIGINT NOT NULL DEFAULT 0,
  created_at TIMESTAMPTZ NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL,

  CONSTRAINT uk_connector_import_runs_identity_scope
    UNIQUE (id, tenant_id, connector_id, import_type),

  CONSTRAINT fk_connector_import_runs_connector
    FOREIGN KEY (connector_id, tenant_id)
    REFERENCES connectors (id, tenant_id),

  CONSTRAINT ck_connector_import_runs_type
    CHECK (import_type IN ('CUSTOMERS', 'INVOICES')),

  CONSTRAINT ck_connector_import_runs_mode
    CHECK (import_mode IN ('FULL', 'INCREMENTAL')),

  CONSTRAINT ck_connector_import_runs_status
    CHECK (
      status IN (
        'REQUESTED',
        'RUNNING',
        'RETRY_SCHEDULED',
        'CANCELLING',
        'COMPLETED',
        'PARTIALLY_COMPLETED',
        'FAILED',
        'CANCELLED'
      )
    ),

  CONSTRAINT ck_connector_import_runs_cursor
    CHECK (
      committed_cursor IS NULL
      OR (
        committed_cursor = btrim(committed_cursor)
        AND committed_cursor <> ''
      )
    ),

  CONSTRAINT ck_connector_import_runs_statistics
    CHECK (
      fetched_count >= 0
      AND accepted_count >= 0
      AND rejected_count >= 0
      AND duplicate_count >= 0
      AND fetched_count = accepted_count + rejected_count + duplicate_count
    ),

  CONSTRAINT ck_connector_import_runs_failure_pair
    CHECK (
      (failure_category IS NULL AND failure_code IS NULL)
      OR (failure_category IS NOT NULL AND failure_code IS NOT NULL)
    ),

  CONSTRAINT ck_connector_import_runs_failure_category
    CHECK (
      failure_category IS NULL
      OR failure_category IN (
        'AUTHENTICATION_FAILED',
        'AUTHORIZATION_FAILED',
        'SOURCE_UNAVAILABLE',
        'TIMEOUT',
        'RATE_LIMITED',
        'INVALID_CURSOR',
        'SOURCE_CONTRACT_VIOLATION',
        'DOWNSTREAM_UNAVAILABLE',
        'UNEXPECTED_FAILURE'
      )
    ),

  CONSTRAINT ck_connector_import_runs_failure_code
    CHECK (
      failure_code IS NULL
      OR failure_code ~ '^[a-z0-9]+(?:-[a-z0-9]+)*$'
    ),

  CONSTRAINT ck_connector_import_runs_retryable_failure
    CHECK (
      status <> 'RETRY_SCHEDULED'
      OR failure_category IN (
        'SOURCE_UNAVAILABLE',
        'TIMEOUT',
        'RATE_LIMITED',
        'DOWNSTREAM_UNAVAILABLE'
      )
    ),

  CONSTRAINT ck_connector_import_runs_attempt_count
    CHECK (attempt_count >= 0),

  CONSTRAINT ck_connector_import_runs_timestamps
    CHECK (
      created_at >= requested_at
      AND updated_at >= created_at
      AND (started_at IS NULL OR started_at >= requested_at)
      AND (finished_at IS NULL OR finished_at >= requested_at)
      AND (finished_at IS NULL OR started_at IS NULL OR finished_at >= started_at)
      AND (next_retry_at IS NULL OR next_retry_at > requested_at)
      AND (next_retry_at IS NULL OR started_at IS NULL OR next_retry_at > started_at)
    ),

  CONSTRAINT ck_connector_import_runs_state
    CHECK (
      (
        status = 'REQUESTED'
        AND attempt_count = 0
        AND fetched_count = 0
        AND started_at IS NULL
        AND finished_at IS NULL
        AND failure_category IS NULL
        AND next_retry_at IS NULL
      )
      OR (
        status IN ('RUNNING', 'CANCELLING')
        AND attempt_count > 0
        AND started_at IS NOT NULL
        AND finished_at IS NULL
        AND failure_category IS NULL
        AND next_retry_at IS NULL
      )
      OR (
        status = 'RETRY_SCHEDULED'
        AND attempt_count > 0
        AND started_at IS NOT NULL
        AND finished_at IS NULL
        AND failure_category IS NOT NULL
        AND next_retry_at IS NOT NULL
      )
      OR (
        status = 'COMPLETED'
        AND attempt_count > 0
        AND started_at IS NOT NULL
        AND finished_at IS NOT NULL
        AND rejected_count = 0
        AND failure_category IS NULL
        AND next_retry_at IS NULL
      )
      OR (
        status = 'PARTIALLY_COMPLETED'
        AND attempt_count > 0
        AND started_at IS NOT NULL
        AND finished_at IS NOT NULL
        AND rejected_count > 0
        AND failure_category IS NULL
        AND next_retry_at IS NULL
      )
      OR (
        status = 'FAILED'
        AND attempt_count > 0
        AND started_at IS NOT NULL
        AND finished_at IS NOT NULL
        AND failure_category IS NOT NULL
        AND next_retry_at IS NULL
      )
      OR (
        status = 'CANCELLED'
        AND finished_at IS NOT NULL
        AND failure_category IS NULL
        AND next_retry_at IS NULL
        AND (
          (attempt_count = 0 AND fetched_count = 0 AND started_at IS NULL)
          OR (attempt_count > 0 AND started_at IS NOT NULL)
        )
      )
    ),

  CONSTRAINT ck_connector_import_runs_version
    CHECK (version >= 0)
);

CREATE UNIQUE INDEX uk_connector_import_runs_active
  ON connector_import_runs (tenant_id, connector_id, import_type)
  WHERE status IN ('REQUESTED', 'RUNNING', 'RETRY_SCHEDULED', 'CANCELLING');

CREATE INDEX ix_connector_import_runs_history
  ON connector_import_runs (tenant_id, connector_id, import_type, requested_at DESC, id DESC);

CREATE TABLE connector_import_checkpoints (
  tenant_id UUID NOT NULL,
  connector_id UUID NOT NULL,
  import_type VARCHAR(32) NOT NULL,
  committed_cursor VARCHAR(2048) NOT NULL,
  last_import_run_id UUID NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL,

  CONSTRAINT pk_connector_import_checkpoints
    PRIMARY KEY (tenant_id, connector_id, import_type),

  CONSTRAINT fk_connector_import_checkpoints_connector
    FOREIGN KEY (connector_id, tenant_id)
    REFERENCES connectors (id, tenant_id),

  CONSTRAINT fk_connector_import_checkpoints_run
    FOREIGN KEY (last_import_run_id, tenant_id, connector_id, import_type)
    REFERENCES connector_import_runs (id, tenant_id, connector_id, import_type),

  CONSTRAINT ck_connector_import_checkpoints_type
    CHECK (import_type IN ('CUSTOMERS', 'INVOICES')),

  CONSTRAINT ck_connector_import_checkpoints_cursor
    CHECK (
      committed_cursor = btrim(committed_cursor)
      AND committed_cursor <> ''
    )
);

CREATE TABLE connector_import_page_acceptances (
  import_run_id UUID NOT NULL,
  acceptance_id UUID NOT NULL,
  accepted_at TIMESTAMPTZ NOT NULL,

  CONSTRAINT pk_connector_import_page_acceptances
    PRIMARY KEY (import_run_id, acceptance_id),

  CONSTRAINT fk_connector_import_page_acceptances_run
    FOREIGN KEY (import_run_id)
    REFERENCES connector_import_runs (id)
    ON DELETE CASCADE
);
