package io.github.khaledshawki.eoc.connectormanagement.application.exception;

import io.github.khaledshawki.eoc.connectormanagement.domain.model.ConnectorName;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ConnectorTenantId;
import java.util.Objects;

public final class ConnectorNameAlreadyExistsException extends RuntimeException {

  public ConnectorNameAlreadyExistsException(
      ConnectorTenantId tenantId, ConnectorName connectorName) {
    super(message(tenantId, connectorName));
  }

  public ConnectorNameAlreadyExistsException(
      ConnectorTenantId tenantId, ConnectorName connectorName, Throwable cause) {
    super(message(tenantId, connectorName), cause);
  }

  private static String message(ConnectorTenantId tenantId, ConnectorName connectorName) {
    Objects.requireNonNull(tenantId, "Connector tenant id cannot be null");
    Objects.requireNonNull(connectorName, "Connector name cannot be null");

    return "Connector name %s already exists for tenant %s"
        .formatted(connectorName.value(), tenantId.value());
  }
}
