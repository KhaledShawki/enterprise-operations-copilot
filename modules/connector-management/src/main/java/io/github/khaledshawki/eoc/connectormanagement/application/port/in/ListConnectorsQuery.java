package io.github.khaledshawki.eoc.connectormanagement.application.port.in;

import java.util.Objects;
import java.util.UUID;

public record ListConnectorsQuery(UUID tenantId) {

  public ListConnectorsQuery {
    Objects.requireNonNull(tenantId, "Tenant id cannot be null");
  }
}
