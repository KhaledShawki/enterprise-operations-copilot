package io.github.khaledshawki.eoc.connectormanagement.application.service;

import io.github.khaledshawki.eoc.connectormanagement.application.exception.ConnectorAccessDeniedException;
import io.github.khaledshawki.eoc.connectormanagement.application.exception.ConnectorAlreadySuspendedException;
import io.github.khaledshawki.eoc.connectormanagement.application.exception.ConnectorNotActiveException;
import io.github.khaledshawki.eoc.connectormanagement.application.exception.ConnectorNotFoundException;
import io.github.khaledshawki.eoc.connectormanagement.application.model.authorization.ConnectorPermission;
import io.github.khaledshawki.eoc.connectormanagement.application.port.in.ConnectorResult;
import io.github.khaledshawki.eoc.connectormanagement.application.port.in.SuspendConnectorCommand;
import io.github.khaledshawki.eoc.connectormanagement.application.port.in.SuspendConnectorUseCase;
import io.github.khaledshawki.eoc.connectormanagement.application.port.out.ConnectorAuthorizationPort;
import io.github.khaledshawki.eoc.connectormanagement.application.port.out.ConnectorRepository;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.Connector;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ConnectorId;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ConnectorStatus;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ConnectorTenantId;
import java.util.Objects;

public final class SuspendConnectorService implements SuspendConnectorUseCase {

  private final ConnectorRepository connectorRepository;
  private final ConnectorAuthorizationPort connectorAuthorizationPort;

  public SuspendConnectorService(
      ConnectorRepository connectorRepository,
      ConnectorAuthorizationPort connectorAuthorizationPort) {
    this.connectorRepository =
        Objects.requireNonNull(connectorRepository, "Connector repository cannot be null");
    this.connectorAuthorizationPort =
        Objects.requireNonNull(
            connectorAuthorizationPort, "Connector authorization port cannot be null");
  }

  @Override
  public ConnectorResult suspend(SuspendConnectorCommand command) {
    Objects.requireNonNull(command, "Command cannot be null");

    ConnectorTenantId tenantId = ConnectorTenantId.of(command.tenantId());
    if (!connectorAuthorizationPort.hasPermission(
        command.actor(), tenantId, ConnectorPermission.ADMINISTER)) {
      throw new ConnectorAccessDeniedException(tenantId, ConnectorPermission.ADMINISTER);
    }

    ConnectorId connectorId = ConnectorId.of(command.connectorId());
    Connector connector =
        connectorRepository
            .findById(tenantId, connectorId)
            .orElseThrow(() -> new ConnectorNotFoundException(tenantId, connectorId));

    if (connector.status() == ConnectorStatus.SUSPENDED) {
      throw new ConnectorAlreadySuspendedException(tenantId, connectorId);
    }

    if (connector.status() != ConnectorStatus.ACTIVE) {
      throw new ConnectorNotActiveException(tenantId, connectorId, connector.status());
    }

    connector.suspend();
    return ConnectorResult.from(connectorRepository.save(connector));
  }
}
