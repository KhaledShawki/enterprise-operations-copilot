package io.github.khaledshawki.eoc.connectormanagement.application.service;

import io.github.khaledshawki.eoc.connectormanagement.application.exception.ConnectorAlreadyActiveException;
import io.github.khaledshawki.eoc.connectormanagement.application.exception.ConnectorNotFoundException;
import io.github.khaledshawki.eoc.connectormanagement.application.port.in.ActivateConnectorCommand;
import io.github.khaledshawki.eoc.connectormanagement.application.port.in.ActivateConnectorUseCase;
import io.github.khaledshawki.eoc.connectormanagement.application.port.in.ConnectorResult;
import io.github.khaledshawki.eoc.connectormanagement.application.port.out.ConnectorRepository;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.Connector;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ConnectorId;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ConnectorStatus;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ConnectorTenantId;
import java.util.Objects;

public final class ActivateConnectorService implements ActivateConnectorUseCase {

  private final ConnectorRepository connectorRepository;

  public ActivateConnectorService(ConnectorRepository connectorRepository) {
    this.connectorRepository =
        Objects.requireNonNull(connectorRepository, "Connector repository cannot be null");
  }

  @Override
  public ConnectorResult activate(ActivateConnectorCommand command) {
    Objects.requireNonNull(command, "Command cannot be null");

    ConnectorTenantId tenantId = ConnectorTenantId.of(command.tenantId());
    ConnectorId connectorId = ConnectorId.of(command.connectorId());
    Connector connector =
        connectorRepository
            .findById(tenantId, connectorId)
            .orElseThrow(() -> new ConnectorNotFoundException(tenantId, connectorId));

    if (connector.status() == ConnectorStatus.ACTIVE) {
      throw new ConnectorAlreadyActiveException(tenantId, connectorId);
    }

    connector.activate();
    return ConnectorResult.from(connectorRepository.save(connector));
  }
}
