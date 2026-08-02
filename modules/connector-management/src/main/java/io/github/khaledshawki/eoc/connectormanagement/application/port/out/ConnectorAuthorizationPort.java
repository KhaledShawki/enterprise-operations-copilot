package io.github.khaledshawki.eoc.connectormanagement.application.port.out;

import io.github.khaledshawki.eoc.connectormanagement.application.model.authorization.ConnectorActor;
import io.github.khaledshawki.eoc.connectormanagement.application.model.authorization.ConnectorPermission;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ConnectorTenantId;

public interface ConnectorAuthorizationPort {

  boolean hasPermission(
      ConnectorActor actor, ConnectorTenantId tenantId, ConnectorPermission permission);
}
