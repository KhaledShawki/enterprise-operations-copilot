ALTER TABLE connectors
  DROP CONSTRAINT ck_connectors_endpoint;

ALTER TABLE connectors
  ADD CONSTRAINT ck_connectors_endpoint
    CHECK (
      endpoint = btrim(endpoint)
      AND endpoint ~* '^(https?|grpcs?)://'
    );
