package io.github.khaledshawki.eoc.connectormanagement.application.port.in;

import io.github.khaledshawki.eoc.connectormanagement.domain.model.ImportMode;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ImportType;
import java.util.Objects;
import java.util.UUID;

public record RequestImportRunCommand(
    UUID tenantId, UUID connectorId, ImportType importType, ImportMode mode) {

  public RequestImportRunCommand {
    Objects.requireNonNull(tenantId, "Tenant id cannot be null");
    Objects.requireNonNull(connectorId, "Connector id cannot be null");
    Objects.requireNonNull(importType, "Import type cannot be null");
    Objects.requireNonNull(mode, "Import mode cannot be null");
  }
}
