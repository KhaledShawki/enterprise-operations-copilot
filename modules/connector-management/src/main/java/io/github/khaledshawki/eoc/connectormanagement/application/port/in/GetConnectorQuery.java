package io.github.khaledshawki.eoc.connectormanagement.application.port.in;

import io.github.khaledshawki.eoc.connectormanagement.application.model.authorization.ConnectorActor;
import java.util.Objects;
import java.util.UUID;

public record GetConnectorQuery(ConnectorActor actor, UUID tenantId, UUID connectorId) {

  public GetConnectorQuery {
    Objects.requireNonNull(actor, "Connector actor cannot be null");
    Objects.requireNonNull(tenantId, "Tenant id cannot be null");
    Objects.requireNonNull(connectorId, "Connector id cannot be null");
  }
}
