package io.github.khaledshawki.eoc.tenantaccess.domain.model;

import java.util.Objects;
import java.util.UUID;

public record TenantMembershipId(UUID value) {

  public TenantMembershipId {
    Objects.requireNonNull(value, "Tenant membership id cannot be null");
  }

  public static TenantMembershipId of(UUID value) {
    return new TenantMembershipId(value);
  }

  public static TenantMembershipId generate() {
    return new TenantMembershipId(UUID.randomUUID());
  }
}
