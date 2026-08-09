CREATE TABLE operations_receivable_settlements (
  id UUID PRIMARY KEY,
  tenant_id UUID NOT NULL,
  customer_id UUID NOT NULL,
  payment_id UUID NOT NULL,
  currency_code VARCHAR(3) NOT NULL,
  version BIGINT NOT NULL DEFAULT 0,
  created_at TIMESTAMPTZ NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL,

  CONSTRAINT uk_operations_receivable_settlements_id_tenant
    UNIQUE (id, tenant_id),

  CONSTRAINT uk_operations_receivable_settlements_tenant_payment
    UNIQUE (tenant_id, payment_id),

  CONSTRAINT fk_operations_receivable_settlements_customer
    FOREIGN KEY (customer_id, tenant_id)
    REFERENCES operations_business_partners (id, tenant_id),

  CONSTRAINT fk_operations_receivable_settlements_payment
    FOREIGN KEY (payment_id, tenant_id)
    REFERENCES operations_payments (id, tenant_id),

  CONSTRAINT ck_operations_receivable_settlements_currency
    CHECK (currency_code ~ '^[A-Z]{3}$'),

  CONSTRAINT ck_operations_receivable_settlements_version
    CHECK (version >= 0),

  CONSTRAINT ck_operations_receivable_settlements_timestamps
    CHECK (updated_at >= created_at)
);

CREATE INDEX ix_operations_receivable_settlements_tenant_customer
  ON operations_receivable_settlements (tenant_id, customer_id, id);

CREATE TABLE operations_receivable_allocations (
  id UUID NOT NULL,
  tenant_id UUID NOT NULL,
  settlement_id UUID NOT NULL,
  invoice_id UUID NOT NULL,
  currency_code VARCHAR(3) NOT NULL,
  amount NUMERIC(38, 9) NOT NULL,
  state VARCHAR(16) NOT NULL,
  allocation_position INTEGER NOT NULL,
  version BIGINT NOT NULL DEFAULT 0,
  created_at TIMESTAMPTZ NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL,

  CONSTRAINT pk_operations_receivable_allocations
    PRIMARY KEY (tenant_id, id),

  CONSTRAINT uk_operations_receivable_allocations_settlement_position
    UNIQUE (tenant_id, settlement_id, allocation_position),

  CONSTRAINT fk_operations_receivable_allocations_settlement
    FOREIGN KEY (settlement_id, tenant_id)
    REFERENCES operations_receivable_settlements (id, tenant_id),

  CONSTRAINT fk_operations_receivable_allocations_invoice
    FOREIGN KEY (invoice_id, tenant_id)
    REFERENCES operations_invoices (id, tenant_id),

  CONSTRAINT ck_operations_receivable_allocations_currency
    CHECK (currency_code ~ '^[A-Z]{3}$'),

  CONSTRAINT ck_operations_receivable_allocations_amount
    CHECK (amount > 0),

  CONSTRAINT ck_operations_receivable_allocations_state
    CHECK (state IN ('ACTIVE', 'REVERSED')),

  CONSTRAINT ck_operations_receivable_allocations_position
    CHECK (allocation_position >= 0),

  CONSTRAINT ck_operations_receivable_allocations_version
    CHECK (version >= 0),

  CONSTRAINT ck_operations_receivable_allocations_timestamps
    CHECK (updated_at >= created_at)
);

CREATE INDEX ix_operations_receivable_allocations_settlement
  ON operations_receivable_allocations (
    tenant_id,
    settlement_id,
    allocation_position
  );

CREATE INDEX ix_operations_receivable_allocations_active_invoice
  ON operations_receivable_allocations (tenant_id, invoice_id, currency_code)
  INCLUDE (amount)
  WHERE state = 'ACTIVE';

CREATE TABLE operations_receivable_settlement_locks (
  tenant_id UUID NOT NULL,
  resource_kind VARCHAR(16) NOT NULL,
  resource_id UUID NOT NULL,

  CONSTRAINT pk_operations_receivable_settlement_locks
    PRIMARY KEY (tenant_id, resource_kind, resource_id),

  CONSTRAINT ck_operations_receivable_settlement_locks_kind
    CHECK (resource_kind IN ('ALLOCATION', 'INVOICE', 'PAYMENT'))
);
