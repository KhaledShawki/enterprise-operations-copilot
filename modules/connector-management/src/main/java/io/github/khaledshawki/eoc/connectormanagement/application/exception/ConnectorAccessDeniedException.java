package io.github.khaledshawki.eoc.connectormanagement.application.exception;

import io.github.khaledshawki.eoc.connectormanagement.application.model.authorization.ConnectorPermission;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ConnectorTenantId;
import java.util.Objects;

public final class ConnectorAccessDeniedException extends RuntimeException {

  public ConnectorAccessDeniedException(
      ConnectorTenantId tenantId, ConnectorPermission permission) {
    super(message(tenantId, permission));
  }

  private static String message(ConnectorTenantId tenantId, ConnectorPermission permission) {
    Objects.requireNonNull(tenantId, "Connector tenant id cannot be null");
    Objects.requireNonNull(permission, "Connector permission cannot be null");

    return "Connector permission %s was denied for tenant %s"
        .formatted(permission, tenantId.value());
  }
}
