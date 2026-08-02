package io.github.khaledshawki.eoc.connectormanagement.application.service;

import io.github.khaledshawki.eoc.connectormanagement.application.exception.ConnectorAccessDeniedException;
import io.github.khaledshawki.eoc.connectormanagement.application.model.authorization.ConnectorPermission;
import io.github.khaledshawki.eoc.connectormanagement.application.port.in.ConnectorResult;
import io.github.khaledshawki.eoc.connectormanagement.application.port.in.ListConnectorsQuery;
import io.github.khaledshawki.eoc.connectormanagement.application.port.in.ListConnectorsResult;
import io.github.khaledshawki.eoc.connectormanagement.application.port.in.ListConnectorsUseCase;
import io.github.khaledshawki.eoc.connectormanagement.application.port.out.ConnectorAuthorizationPort;
import io.github.khaledshawki.eoc.connectormanagement.application.port.out.ConnectorRepository;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.Connector;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ConnectorTenantId;
import java.util.Comparator;
import java.util.Objects;

public final class ListConnectorsService implements ListConnectorsUseCase {

  private final ConnectorRepository connectorRepository;
  private final ConnectorAuthorizationPort connectorAuthorizationPort;

  public ListConnectorsService(
      ConnectorRepository connectorRepository,
      ConnectorAuthorizationPort connectorAuthorizationPort) {
    this.connectorRepository =
        Objects.requireNonNull(connectorRepository, "Connector repository cannot be null");
    this.connectorAuthorizationPort =
        Objects.requireNonNull(
            connectorAuthorizationPort, "Connector authorization port cannot be null");
  }

  @Override
  public ListConnectorsResult list(ListConnectorsQuery query) {
    Objects.requireNonNull(query, "Query cannot be null");

    ConnectorTenantId tenantId = ConnectorTenantId.of(query.tenantId());
    if (!connectorAuthorizationPort.hasPermission(
        query.actor(), tenantId, ConnectorPermission.READ)) {
      throw new ConnectorAccessDeniedException(tenantId, ConnectorPermission.READ);
    }

    return new ListConnectorsResult(
        connectorRepository.findAllByTenantId(tenantId).stream()
            .sorted(
                Comparator.comparing((Connector connector) -> connector.name().value())
                    .thenComparing(connector -> connector.id().value()))
            .map(ConnectorResult::from)
            .toList());
  }
}
