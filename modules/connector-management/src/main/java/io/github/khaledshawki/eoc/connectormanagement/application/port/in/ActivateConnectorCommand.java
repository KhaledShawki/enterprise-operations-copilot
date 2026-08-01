package io.github.khaledshawki.eoc.connectormanagement.application.port.in;

import java.util.Objects;
import java.util.UUID;

public record ActivateConnectorCommand(UUID tenantId, UUID connectorId) {

  public ActivateConnectorCommand {
    Objects.requireNonNull(tenantId, "Tenant id cannot be null");
    Objects.requireNonNull(connectorId, "Connector id cannot be null");
  }
}
