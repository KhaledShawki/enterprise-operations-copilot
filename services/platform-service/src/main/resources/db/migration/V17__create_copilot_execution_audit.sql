CREATE TABLE copilot_execution_audit_events (
  audit_event_id UUID PRIMARY KEY,
  execution_id UUID NOT NULL,
  event_type VARCHAR(16) NOT NULL,
  issuer VARCHAR(2048) NOT NULL,
  subject VARCHAR(512) NOT NULL,
  tenant_id UUID NOT NULL,
  business_date DATE,
  question_sha256 VARCHAR(64) NOT NULL,
  question_length INTEGER NOT NULL,
  answer_sha256 VARCHAR(64),
  answer_length INTEGER,
  failure_code VARCHAR(64),
  occurred_at TIMESTAMPTZ NOT NULL,

  CONSTRAINT ck_copilot_audit_event_type
    CHECK (event_type IN ('STARTED', 'SUCCEEDED', 'FAILED')),
  CONSTRAINT ck_copilot_audit_question_sha256
    CHECK (question_sha256 ~ '^[0-9a-f]{64}$'),
  CONSTRAINT ck_copilot_audit_question_length
    CHECK (question_length BETWEEN 1 AND 2000),
  CONSTRAINT ck_copilot_audit_event_contract
    CHECK (
      (event_type = 'STARTED'
        AND answer_sha256 IS NULL
        AND answer_length IS NULL
        AND failure_code IS NULL)
      OR (event_type = 'SUCCEEDED'
        AND answer_sha256 ~ '^[0-9a-f]{64}$'
        AND answer_length BETWEEN 1 AND 20000
        AND failure_code IS NULL)
      OR (event_type = 'FAILED'
        AND answer_sha256 IS NULL
        AND answer_length IS NULL
        AND failure_code IN (
          'ACCESS_DENIED',
          'INVALID_ARGUMENTS',
          'NOT_FOUND',
          'DATA_UNAVAILABLE',
          'DATA_CORRUPTION',
          'MODEL_PROTOCOL',
          'MODEL_UNAVAILABLE',
          'ORCHESTRATION_LIMIT',
          'ANSWER_GROUNDING',
          'UNEXPECTED'
        ))
    )
);

CREATE UNIQUE INDEX ux_copilot_audit_started_execution
  ON copilot_execution_audit_events (execution_id)
  WHERE event_type = 'STARTED';

CREATE UNIQUE INDEX ux_copilot_audit_terminal_execution
  ON copilot_execution_audit_events (execution_id)
  WHERE event_type IN ('SUCCEEDED', 'FAILED');

CREATE INDEX ix_copilot_audit_tenant_history
  ON copilot_execution_audit_events (tenant_id, occurred_at DESC, audit_event_id DESC);

CREATE INDEX ix_copilot_audit_principal_history
  ON copilot_execution_audit_events (issuer, subject, occurred_at DESC, audit_event_id DESC);

CREATE TABLE copilot_execution_audit_groundings (
  audit_event_id UUID NOT NULL,
  grounding_index SMALLINT NOT NULL,
  tool_call_id VARCHAR(128) NOT NULL,
  tool_name VARCHAR(64) NOT NULL,

  PRIMARY KEY (audit_event_id, grounding_index),
  CONSTRAINT fk_copilot_audit_grounding_event
    FOREIGN KEY (audit_event_id)
    REFERENCES copilot_execution_audit_events (audit_event_id),
  CONSTRAINT uk_copilot_audit_grounding_call
    UNIQUE (audit_event_id, tool_call_id),
  CONSTRAINT ck_copilot_audit_grounding_index
    CHECK (grounding_index BETWEEN 0 AND 2),
  CONSTRAINT ck_copilot_audit_tool_name
    CHECK (tool_name IN ('get_receivable', 'list_receivables', 'get_receivables_summary'))
);

CREATE TABLE copilot_execution_audit_evidence (
  audit_event_id UUID NOT NULL,
  grounding_index SMALLINT NOT NULL,
  evidence_index INTEGER NOT NULL,
  source_event_id UUID NOT NULL,
  aggregate_version BIGINT NOT NULL,
  occurred_at TIMESTAMPTZ NOT NULL,

  PRIMARY KEY (audit_event_id, grounding_index, evidence_index),
  CONSTRAINT fk_copilot_audit_evidence_grounding
    FOREIGN KEY (audit_event_id, grounding_index)
    REFERENCES copilot_execution_audit_groundings (audit_event_id, grounding_index),
  CONSTRAINT ck_copilot_audit_evidence_index
    CHECK (evidence_index >= 0),
  CONSTRAINT ck_copilot_audit_evidence_version
    CHECK (aggregate_version > 0)
);
