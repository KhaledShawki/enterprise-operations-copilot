package io.github.khaledshawki.eoc.tenantaccess.application.port.in;

import java.util.Objects;
import java.util.UUID;

public record GetTenantQuery(UUID tenantId) {

  public GetTenantQuery {
    Objects.requireNonNull(tenantId, "Tenant id cannot be null");
  }
}
