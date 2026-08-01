package io.github.khaledshawki.eoc.connectormanagement.application.port.in;

import java.util.Objects;
import java.util.UUID;

public record SuspendConnectorCommand(UUID tenantId, UUID connectorId) {

  public SuspendConnectorCommand {
    Objects.requireNonNull(tenantId, "Tenant id cannot be null");
    Objects.requireNonNull(connectorId, "Connector id cannot be null");
  }
}
