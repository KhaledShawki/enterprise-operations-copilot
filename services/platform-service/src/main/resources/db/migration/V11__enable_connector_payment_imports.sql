ALTER TABLE connector_import_runs
  DROP CONSTRAINT ck_connector_import_runs_type;

ALTER TABLE connector_import_runs
  ADD CONSTRAINT ck_connector_import_runs_type
  CHECK (import_type IN ('CUSTOMERS', 'INVOICES', 'PAYMENTS'));

ALTER TABLE connector_import_checkpoints
  DROP CONSTRAINT ck_connector_import_checkpoints_type;

ALTER TABLE connector_import_checkpoints
  ADD CONSTRAINT ck_connector_import_checkpoints_type
  CHECK (import_type IN ('CUSTOMERS', 'INVOICES', 'PAYMENTS'));
