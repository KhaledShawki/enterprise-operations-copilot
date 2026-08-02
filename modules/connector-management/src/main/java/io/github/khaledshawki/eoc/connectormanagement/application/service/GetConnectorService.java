package io.github.khaledshawki.eoc.connectormanagement.application.service;

import io.github.khaledshawki.eoc.connectormanagement.application.exception.ConnectorAccessDeniedException;
import io.github.khaledshawki.eoc.connectormanagement.application.exception.ConnectorNotFoundException;
import io.github.khaledshawki.eoc.connectormanagement.application.model.authorization.ConnectorPermission;
import io.github.khaledshawki.eoc.connectormanagement.application.port.in.ConnectorResult;
import io.github.khaledshawki.eoc.connectormanagement.application.port.in.GetConnectorQuery;
import io.github.khaledshawki.eoc.connectormanagement.application.port.in.GetConnectorUseCase;
import io.github.khaledshawki.eoc.connectormanagement.application.port.out.ConnectorAuthorizationPort;
import io.github.khaledshawki.eoc.connectormanagement.application.port.out.ConnectorRepository;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ConnectorId;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ConnectorTenantId;
import java.util.Objects;

public final class GetConnectorService implements GetConnectorUseCase {

  private final ConnectorRepository connectorRepository;
  private final ConnectorAuthorizationPort connectorAuthorizationPort;

  public GetConnectorService(
      ConnectorRepository connectorRepository,
      ConnectorAuthorizationPort connectorAuthorizationPort) {
    this.connectorRepository =
        Objects.requireNonNull(connectorRepository, "Connector repository cannot be null");
    this.connectorAuthorizationPort =
        Objects.requireNonNull(
            connectorAuthorizationPort, "Connector authorization port cannot be null");
  }

  @Override
  public ConnectorResult get(GetConnectorQuery query) {
    Objects.requireNonNull(query, "Query cannot be null");

    ConnectorTenantId tenantId = ConnectorTenantId.of(query.tenantId());
    if (!connectorAuthorizationPort.hasPermission(
        query.actor(), tenantId, ConnectorPermission.READ)) {
      throw new ConnectorAccessDeniedException(tenantId, ConnectorPermission.READ);
    }

    ConnectorId connectorId = ConnectorId.of(query.connectorId());

    return connectorRepository
        .findById(tenantId, connectorId)
        .map(ConnectorResult::from)
        .orElseThrow(() -> new ConnectorNotFoundException(tenantId, connectorId));
  }
}
