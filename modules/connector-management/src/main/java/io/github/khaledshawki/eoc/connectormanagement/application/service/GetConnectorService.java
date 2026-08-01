package io.github.khaledshawki.eoc.connectormanagement.application.service;

import io.github.khaledshawki.eoc.connectormanagement.application.exception.ConnectorNotFoundException;
import io.github.khaledshawki.eoc.connectormanagement.application.port.in.ConnectorResult;
import io.github.khaledshawki.eoc.connectormanagement.application.port.in.GetConnectorQuery;
import io.github.khaledshawki.eoc.connectormanagement.application.port.in.GetConnectorUseCase;
import io.github.khaledshawki.eoc.connectormanagement.application.port.out.ConnectorRepository;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ConnectorId;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ConnectorTenantId;
import java.util.Objects;

public final class GetConnectorService implements GetConnectorUseCase {

  private final ConnectorRepository connectorRepository;

  public GetConnectorService(ConnectorRepository connectorRepository) {
    this.connectorRepository =
        Objects.requireNonNull(connectorRepository, "Connector repository cannot be null");
  }

  @Override
  public ConnectorResult get(GetConnectorQuery query) {
    Objects.requireNonNull(query, "Query cannot be null");

    ConnectorTenantId tenantId = ConnectorTenantId.of(query.tenantId());
    ConnectorId connectorId = ConnectorId.of(query.connectorId());

    return connectorRepository
        .findById(tenantId, connectorId)
        .map(ConnectorResult::from)
        .orElseThrow(() -> new ConnectorNotFoundException(tenantId, connectorId));
  }
}
