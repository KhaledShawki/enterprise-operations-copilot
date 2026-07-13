package io.github.khaledshawki.eoc.tenantaccess.domain.model;

import java.util.Objects;
import java.util.UUID;

public record TenantId(UUID value) {
  public TenantId {
    Objects.requireNonNull(value, "Tenant id cannot be null");
  }

  public static TenantId of(UUID value) {
    return new TenantId(value);
  }

  public static TenantId generate() {
    return new TenantId(UUID.randomUUID());
  }
}
