package io.github.khaledshawki.eoc.connectormanagement.application.exception;

import io.github.khaledshawki.eoc.connectormanagement.domain.model.ConnectorId;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ConnectorStatus;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ConnectorTenantId;
import java.util.Objects;

public final class ConnectorCannotStartImportException extends RuntimeException {

  public ConnectorCannotStartImportException(
      ConnectorTenantId tenantId, ConnectorId connectorId, ConnectorStatus status) {
    super(message(tenantId, connectorId, status));
  }

  private static String message(
      ConnectorTenantId tenantId, ConnectorId connectorId, ConnectorStatus status) {
    Objects.requireNonNull(tenantId, "Connector tenant id cannot be null");
    Objects.requireNonNull(connectorId, "Connector id cannot be null");
    Objects.requireNonNull(status, "Connector status cannot be null");
    return "Connector %s for tenant %s cannot start an import while its status is %s"
        .formatted(connectorId.value(), tenantId.value(), status);
  }
}
