package io.github.khaledshawki.eoc.connectormanagement.application.port.in;

import java.util.Objects;
import java.util.UUID;

public record GetConnectorQuery(UUID tenantId, UUID connectorId) {

  public GetConnectorQuery {
    Objects.requireNonNull(tenantId, "Tenant id cannot be null");
    Objects.requireNonNull(connectorId, "Connector id cannot be null");
  }
}
