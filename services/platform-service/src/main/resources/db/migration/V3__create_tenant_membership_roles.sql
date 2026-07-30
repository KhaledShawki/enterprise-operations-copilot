CREATE TABLE tenant_membership_roles (
  tenant_membership_id UUID NOT NULL,
  role_key VARCHAR(63) NOT NULL,

  PRIMARY KEY (tenant_membership_id, role_key),

  CONSTRAINT fk_tenant_membership_roles_membership
    FOREIGN KEY (tenant_membership_id)
      REFERENCES tenant_memberships (id)
      ON DELETE CASCADE,

  CONSTRAINT ck_tenant_membership_roles_role_key_format
    CHECK (role_key ~ '^[a-z0-9]+(?:-[a-z0-9]+)*$')
);

CREATE INDEX ix_tenant_membership_roles_role_key
  ON tenant_membership_roles (role_key);
