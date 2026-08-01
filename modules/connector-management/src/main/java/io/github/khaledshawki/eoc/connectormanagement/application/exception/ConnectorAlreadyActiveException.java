package io.github.khaledshawki.eoc.connectormanagement.application.exception;

import io.github.khaledshawki.eoc.connectormanagement.domain.model.ConnectorId;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ConnectorTenantId;
import java.util.Objects;

public final class ConnectorAlreadyActiveException extends RuntimeException {

  public ConnectorAlreadyActiveException(ConnectorTenantId tenantId, ConnectorId connectorId) {
    super(message(tenantId, connectorId));
  }

  private static String message(ConnectorTenantId tenantId, ConnectorId connectorId) {
    Objects.requireNonNull(tenantId, "Connector tenant id cannot be null");
    Objects.requireNonNull(connectorId, "Connector id cannot be null");

    return "Connector %s is already active for tenant %s"
        .formatted(connectorId.value(), tenantId.value());
  }
}
