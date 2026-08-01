package io.github.khaledshawki.eoc.connectormanagement.application.service;

import io.github.khaledshawki.eoc.connectormanagement.application.port.in.ConnectorResult;
import io.github.khaledshawki.eoc.connectormanagement.application.port.in.ListConnectorsQuery;
import io.github.khaledshawki.eoc.connectormanagement.application.port.in.ListConnectorsResult;
import io.github.khaledshawki.eoc.connectormanagement.application.port.in.ListConnectorsUseCase;
import io.github.khaledshawki.eoc.connectormanagement.application.port.out.ConnectorRepository;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.Connector;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ConnectorTenantId;
import java.util.Comparator;
import java.util.Objects;

public final class ListConnectorsService implements ListConnectorsUseCase {

  private final ConnectorRepository connectorRepository;

  public ListConnectorsService(ConnectorRepository connectorRepository) {
    this.connectorRepository =
        Objects.requireNonNull(connectorRepository, "Connector repository cannot be null");
  }

  @Override
  public ListConnectorsResult list(ListConnectorsQuery query) {
    Objects.requireNonNull(query, "Query cannot be null");

    ConnectorTenantId tenantId = ConnectorTenantId.of(query.tenantId());
    return new ListConnectorsResult(
        connectorRepository.findAllByTenantId(tenantId).stream()
            .sorted(
                Comparator.comparing((Connector connector) -> connector.name().value())
                    .thenComparing(connector -> connector.id().value()))
            .map(ConnectorResult::from)
            .toList());
  }
}
