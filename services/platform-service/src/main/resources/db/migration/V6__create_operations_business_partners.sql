CREATE TABLE operations_business_partners (
  id UUID PRIMARY KEY,
  tenant_id UUID NOT NULL,
  partner_number VARCHAR(100) NOT NULL,
  display_name VARCHAR(255) NOT NULL,
  email_address VARCHAR(320),
  version BIGINT NOT NULL DEFAULT 0,
  created_at TIMESTAMPTZ NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL,

  CONSTRAINT uk_operations_business_partners_id_tenant
    UNIQUE (id, tenant_id),

  CONSTRAINT ck_operations_business_partners_partner_number
    CHECK (
      partner_number = btrim(partner_number)
      AND partner_number <> ''
    ),

  CONSTRAINT ck_operations_business_partners_display_name
    CHECK (
      display_name = btrim(display_name)
      AND display_name <> ''
    ),

  CONSTRAINT ck_operations_business_partners_email_address
    CHECK (
      email_address IS NULL
      OR (
        email_address = btrim(email_address)
        AND email_address <> ''
      )
    ),

  CONSTRAINT ck_operations_business_partners_version
    CHECK (version >= 0),

  CONSTRAINT ck_operations_business_partners_timestamps
    CHECK (updated_at >= created_at)
);

CREATE INDEX ix_operations_business_partners_tenant_name
  ON operations_business_partners (tenant_id, display_name, id);

CREATE TABLE operations_business_partner_roles (
  business_partner_id UUID NOT NULL,
  tenant_id UUID NOT NULL,
  role VARCHAR(32) NOT NULL,

  CONSTRAINT pk_operations_business_partner_roles
    PRIMARY KEY (business_partner_id, tenant_id, role),

  CONSTRAINT fk_operations_business_partner_roles_partner
    FOREIGN KEY (business_partner_id, tenant_id)
    REFERENCES operations_business_partners (id, tenant_id)
    ON DELETE CASCADE,

  CONSTRAINT ck_operations_business_partner_roles_role
    CHECK (role IN ('CUSTOMER', 'VENDOR'))
);

CREATE TABLE operations_business_partner_source_mappings (
  tenant_id UUID NOT NULL,
  source_system_id UUID NOT NULL,
  source_identity_kind VARCHAR(32) NOT NULL,
  source_identity_value VARCHAR(255) NOT NULL,
  business_partner_id UUID NOT NULL,
  source_version VARCHAR(512) NOT NULL,
  source_modified_at TIMESTAMPTZ,
  version BIGINT NOT NULL DEFAULT 0,
  created_at TIMESTAMPTZ NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL,

  CONSTRAINT pk_operations_business_partner_source_mappings
    PRIMARY KEY (
      tenant_id,
      source_system_id,
      source_identity_kind,
      source_identity_value
    ),

  CONSTRAINT fk_operations_business_partner_source_mappings_partner
    FOREIGN KEY (business_partner_id, tenant_id)
    REFERENCES operations_business_partners (id, tenant_id),

  CONSTRAINT ck_operations_business_partner_source_identity_kind
    CHECK (
      source_identity_kind IN ('SOURCE_RECORD_ID', 'CANONICAL_RECORD_HASH')
    ),

  CONSTRAINT ck_operations_business_partner_source_identity_value
    CHECK (
      source_identity_value = btrim(source_identity_value)
      AND source_identity_value <> ''
      AND (
        source_identity_kind <> 'CANONICAL_RECORD_HASH'
        OR source_identity_value ~ '^[a-f0-9]{64}$'
      )
    ),

  CONSTRAINT ck_operations_business_partner_source_version
    CHECK (
      source_version = btrim(source_version)
      AND source_version <> ''
    ),

  CONSTRAINT ck_operations_business_partner_source_mapping_version
    CHECK (version >= 0),

  CONSTRAINT ck_operations_business_partner_source_mapping_timestamps
    CHECK (updated_at >= created_at)
);

CREATE INDEX ix_operations_business_partner_source_mappings_partner
  ON operations_business_partner_source_mappings (
    tenant_id,
    business_partner_id
  );

CREATE TABLE operations_business_partner_import_receipts (
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

  CONSTRAINT pk_operations_business_partner_import_receipts
    PRIMARY KEY (
      tenant_id,
      source_system_id,
      import_batch_id,
      page_acceptance_id
    ),

  CONSTRAINT ck_operations_business_partner_import_receipt_fingerprint
    CHECK (payload_fingerprint ~ '^[a-f0-9]{64}$'),

  CONSTRAINT ck_operations_business_partner_import_receipt_counts
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

CREATE INDEX ix_operations_business_partner_import_receipts_history
  ON operations_business_partner_import_receipts (
    tenant_id,
    source_system_id,
    accepted_at DESC,
    import_batch_id,
    page_acceptance_id
  );
