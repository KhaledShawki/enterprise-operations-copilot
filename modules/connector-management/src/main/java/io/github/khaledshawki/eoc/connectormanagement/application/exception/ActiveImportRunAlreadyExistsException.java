package io.github.khaledshawki.eoc.connectormanagement.application.exception;

import io.github.khaledshawki.eoc.connectormanagement.domain.model.ConnectorId;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ConnectorTenantId;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ImportType;
import java.util.Objects;

public final class ActiveImportRunAlreadyExistsException extends RuntimeException {

  public ActiveImportRunAlreadyExistsException(
      ConnectorTenantId tenantId, ConnectorId connectorId, ImportType importType) {
    super(message(tenantId, connectorId, importType));
  }

  public ActiveImportRunAlreadyExistsException(
      ConnectorTenantId tenantId, ConnectorId connectorId, ImportType importType, Throwable cause) {
    super(message(tenantId, connectorId, importType), cause);
  }

  private static String message(
      ConnectorTenantId tenantId, ConnectorId connectorId, ImportType importType) {
    Objects.requireNonNull(tenantId, "Connector tenant id cannot be null");
    Objects.requireNonNull(connectorId, "Connector id cannot be null");
    Objects.requireNonNull(importType, "Import type cannot be null");
    return "An active %s import already exists for connector %s in tenant %s"
        .formatted(importType, connectorId.value(), tenantId.value());
  }
}
