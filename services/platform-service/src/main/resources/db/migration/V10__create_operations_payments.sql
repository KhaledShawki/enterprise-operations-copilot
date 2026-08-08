CREATE TABLE operations_payments (
  id UUID PRIMARY KEY,
  tenant_id UUID NOT NULL,
  customer_id UUID NOT NULL,
  currency_code VARCHAR(3) NOT NULL,
  amount NUMERIC(38, 9) NOT NULL,
  payment_date DATE NOT NULL,
  reversed BOOLEAN NOT NULL,
  version BIGINT NOT NULL DEFAULT 0,
  created_at TIMESTAMPTZ NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL,

  CONSTRAINT uk_operations_payments_id_tenant
    UNIQUE (id, tenant_id),

  CONSTRAINT fk_operations_payments_customer
    FOREIGN KEY (customer_id, tenant_id)
    REFERENCES operations_business_partners (id, tenant_id),

  CONSTRAINT ck_operations_payments_currency
    CHECK (currency_code ~ '^[A-Z]{3}$'),

  CONSTRAINT ck_operations_payments_amount
    CHECK (amount > 0),

  CONSTRAINT ck_operations_payments_version
    CHECK (version >= 0),

  CONSTRAINT ck_operations_payments_timestamps
    CHECK (updated_at >= created_at)
);

CREATE INDEX ix_operations_payments_tenant_payment_date
  ON operations_payments (tenant_id, payment_date DESC, id);

CREATE INDEX ix_operations_payments_tenant_customer
  ON operations_payments (tenant_id, customer_id, payment_date DESC, id);

CREATE TABLE operations_payment_source_mappings (
  tenant_id UUID NOT NULL,
  source_system_id UUID NOT NULL,
  source_identity_kind VARCHAR(32) NOT NULL,
  source_identity_value VARCHAR(255) NOT NULL,
  payment_id UUID NOT NULL,
  source_version VARCHAR(512) NOT NULL,
  source_modified_at TIMESTAMPTZ,
  payload_fingerprint VARCHAR(64) NOT NULL,
  version BIGINT NOT NULL DEFAULT 0,
  created_at TIMESTAMPTZ NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL,

  CONSTRAINT pk_operations_payment_source_mappings
    PRIMARY KEY (
      tenant_id,
      source_system_id,
      source_identity_kind,
      source_identity_value
    ),

  CONSTRAINT fk_operations_payment_source_mappings_payment
    FOREIGN KEY (payment_id, tenant_id)
    REFERENCES operations_payments (id, tenant_id),

  CONSTRAINT ck_operations_payment_source_identity_kind
    CHECK (
      source_identity_kind IN ('SOURCE_RECORD_ID', 'CANONICAL_RECORD_HASH')
    ),

  CONSTRAINT ck_operations_payment_source_identity_value
    CHECK (
      source_identity_value = btrim(source_identity_value)
      AND source_identity_value <> ''
      AND (
        source_identity_kind <> 'CANONICAL_RECORD_HASH'
        OR source_identity_value ~ '^[a-f0-9]{64}$'
      )
    ),

  CONSTRAINT ck_operations_payment_source_version
    CHECK (
      source_version = btrim(source_version)
      AND source_version <> ''
    ),

  CONSTRAINT ck_operations_payment_source_fingerprint
    CHECK (payload_fingerprint ~ '^[a-f0-9]{64}$'),

  CONSTRAINT ck_operations_payment_source_mapping_version
    CHECK (version >= 0),

  CONSTRAINT ck_operations_payment_source_mapping_timestamps
    CHECK (updated_at >= created_at)
);

CREATE INDEX ix_operations_payment_source_mappings_payment
  ON operations_payment_source_mappings (tenant_id, payment_id);

CREATE TABLE operations_payment_import_receipts (
  tenant_id UUID NOT NULL,
  source_system_id UUID NOT NULL,
  import_batch_id UUID NOT NULL,
  page_acceptance_id UUID NOT NULL,
  payload_fingerprint VARCHAR(64) NOT NULL,
  fetched_count BIGINT NOT NULL,
  created_count BIGINT NOT NULL,
  updated_count BIGINT NOT NULL,
  duplicate_count BIGINT NOT NULL,
  stale_count BIGINT NOT NULL,
  accepted_at TIMESTAMPTZ NOT NULL,

  CONSTRAINT pk_operations_payment_import_receipts
    PRIMARY KEY (
      tenant_id,
      source_system_id,
      import_batch_id,
      page_acceptance_id
    ),

  CONSTRAINT ck_operations_payment_import_receipt_fingerprint
    CHECK (payload_fingerprint ~ '^[a-f0-9]{64}$'),

  CONSTRAINT ck_operations_payment_import_receipt_counts
    CHECK (
      fetched_count >= 0
      AND created_count >= 0
      AND updated_count >= 0
      AND duplicate_count >= 0
      AND stale_count >= 0
      AND fetched_count = (
        created_count + updated_count + duplicate_count + stale_count
      )
    )
);

CREATE INDEX ix_operations_payment_import_receipts_history
  ON operations_payment_import_receipts (
    tenant_id,
    source_system_id,
    accepted_at DESC,
    import_batch_id,
    page_acceptance_id
  );
