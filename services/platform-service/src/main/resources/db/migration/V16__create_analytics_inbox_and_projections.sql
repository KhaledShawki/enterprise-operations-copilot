CREATE TABLE analytics_inbox_events (
  event_id UUID PRIMARY KEY,
  event_type VARCHAR(128) NOT NULL,
  schema_version INTEGER NOT NULL,
  tenant_id UUID NOT NULL,
  aggregate_type VARCHAR(64) NOT NULL,
  aggregate_id UUID NOT NULL,
  aggregate_version BIGINT NOT NULL,
  payload JSONB NOT NULL,
  content_fingerprint VARCHAR(64) NOT NULL,
  projection_status VARCHAR(16) NOT NULL,
  occurred_at TIMESTAMPTZ NOT NULL,
  received_at TIMESTAMPTZ NOT NULL,
  processed_at TIMESTAMPTZ NOT NULL,

  CONSTRAINT ck_analytics_inbox_event_type
    CHECK (
      event_type IN (
        'operations.business-partner.synchronized.v1',
        'operations.invoice.synchronized.v1',
        'operations.payment.synchronized.v1',
        'operations.receivable-allocation.applied.v1',
        'operations.receivable-allocation.reversed.v1'
      )
    ),

  CONSTRAINT ck_analytics_inbox_schema_version
    CHECK (schema_version = 1),

  CONSTRAINT ck_analytics_inbox_aggregate_contract
    CHECK (
      (event_type = 'operations.business-partner.synchronized.v1'
        AND aggregate_type = 'BUSINESS_PARTNER')
      OR (event_type = 'operations.invoice.synchronized.v1'
        AND aggregate_type = 'INVOICE')
      OR (event_type = 'operations.payment.synchronized.v1'
        AND aggregate_type = 'PAYMENT')
      OR (event_type IN (
          'operations.receivable-allocation.applied.v1',
          'operations.receivable-allocation.reversed.v1'
        )
        AND aggregate_type = 'RECEIVABLE_SETTLEMENT')
    ),

  CONSTRAINT ck_analytics_inbox_aggregate_version
    CHECK (aggregate_version > 0),

  CONSTRAINT uk_analytics_inbox_aggregate_version
    UNIQUE (tenant_id, aggregate_type, aggregate_id, aggregate_version),

  CONSTRAINT ck_analytics_inbox_payload
    CHECK (jsonb_typeof(payload) = 'object'),

  CONSTRAINT ck_analytics_inbox_fingerprint
    CHECK (content_fingerprint ~ '^[0-9a-f]{64}$'),

  CONSTRAINT ck_analytics_inbox_projection_status
    CHECK (
      (event_type IN (
          'operations.business-partner.synchronized.v1',
          'operations.invoice.synchronized.v1'
        )
        AND projection_status = 'APPLIED')
      OR (event_type IN (
          'operations.payment.synchronized.v1',
          'operations.receivable-allocation.applied.v1',
          'operations.receivable-allocation.reversed.v1'
        )
        AND projection_status = 'IGNORED')
    ),

  CONSTRAINT ck_analytics_inbox_timestamps
    CHECK (processed_at >= received_at)
);

CREATE INDEX ix_analytics_inbox_tenant_history
  ON analytics_inbox_events (tenant_id, occurred_at DESC, event_id DESC);

CREATE INDEX ix_analytics_inbox_aggregate_history
  ON analytics_inbox_events (
    tenant_id,
    aggregate_type,
    aggregate_id,
    aggregate_version,
    event_id
  );

CREATE TABLE analytics_business_partner_projections (
  tenant_id UUID NOT NULL,
  business_partner_id UUID NOT NULL,
  partner_number VARCHAR(100) NOT NULL,
  display_name VARCHAR(255) NOT NULL,
  roles JSONB NOT NULL,
  source_event_id UUID NOT NULL,
  aggregate_version BIGINT NOT NULL,
  occurred_at TIMESTAMPTZ NOT NULL,
  projected_at TIMESTAMPTZ NOT NULL,

  PRIMARY KEY (tenant_id, business_partner_id),

  CONSTRAINT fk_analytics_business_partner_source_event
    FOREIGN KEY (source_event_id)
    REFERENCES analytics_inbox_events (event_id),

  CONSTRAINT ck_analytics_business_partner_number
    CHECK (btrim(partner_number) <> ''),

  CONSTRAINT ck_analytics_business_partner_display_name
    CHECK (btrim(display_name) <> ''),

  CONSTRAINT ck_analytics_business_partner_roles
    CHECK (
      jsonb_typeof(roles) = 'array'
      AND jsonb_array_length(roles) > 0
    ),

  CONSTRAINT ck_analytics_business_partner_version
    CHECK (aggregate_version > 0)
);

CREATE TABLE analytics_invoice_receivable_projections (
  tenant_id UUID NOT NULL,
  invoice_id UUID NOT NULL,
  customer_id UUID NOT NULL,
  invoice_number VARCHAR(100) NOT NULL,
  original_amount NUMERIC(38, 12) NOT NULL,
  paid_amount NUMERIC(38, 12) NOT NULL,
  currency VARCHAR(3) NOT NULL,
  issue_date DATE NOT NULL,
  due_date DATE NOT NULL,
  cancelled BOOLEAN NOT NULL,
  status VARCHAR(32) NOT NULL,
  source_event_id UUID NOT NULL,
  aggregate_version BIGINT NOT NULL,
  occurred_at TIMESTAMPTZ NOT NULL,
  projected_at TIMESTAMPTZ NOT NULL,

  PRIMARY KEY (tenant_id, invoice_id),

  CONSTRAINT fk_analytics_invoice_source_event
    FOREIGN KEY (source_event_id)
    REFERENCES analytics_inbox_events (event_id),

  CONSTRAINT ck_analytics_invoice_number
    CHECK (btrim(invoice_number) <> ''),

  CONSTRAINT ck_analytics_invoice_amounts
    CHECK (
      original_amount >= 0
      AND paid_amount >= 0
      AND paid_amount <= original_amount
    ),

  CONSTRAINT ck_analytics_invoice_currency
    CHECK (currency ~ '^[A-Z]{3}$'),

  CONSTRAINT ck_analytics_invoice_dates
    CHECK (due_date >= issue_date),

  CONSTRAINT ck_analytics_invoice_status
    CHECK (
      (cancelled AND status = 'CANCELLED')
      OR (
        NOT cancelled
        AND paid_amount = original_amount
        AND status = 'PAID'
      )
      OR (
        NOT cancelled
        AND paid_amount > 0
        AND paid_amount < original_amount
        AND status = 'PARTIALLY_PAID'
      )
      OR (
        NOT cancelled
        AND paid_amount = 0
        AND original_amount > 0
        AND status = 'OPEN'
      )
    ),

  CONSTRAINT ck_analytics_invoice_version
    CHECK (aggregate_version > 0)
);

CREATE INDEX ix_analytics_invoice_customer
  ON analytics_invoice_receivable_projections (tenant_id, customer_id, invoice_id);

CREATE INDEX ix_analytics_invoice_due
  ON analytics_invoice_receivable_projections (tenant_id, status, due_date, invoice_id);
