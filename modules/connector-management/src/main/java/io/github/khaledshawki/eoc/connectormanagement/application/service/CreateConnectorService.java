package io.github.khaledshawki.eoc.connectormanagement.application.service;

import io.github.khaledshawki.eoc.connectormanagement.application.exception.ConnectorAccessDeniedException;
import io.github.khaledshawki.eoc.connectormanagement.application.exception.ConnectorNameAlreadyExistsException;
import io.github.khaledshawki.eoc.connectormanagement.application.exception.InvalidConnectorConfigurationException;
import io.github.khaledshawki.eoc.connectormanagement.application.model.authorization.ConnectorPermission;
import io.github.khaledshawki.eoc.connectormanagement.application.port.in.ConnectorResult;
import io.github.khaledshawki.eoc.connectormanagement.application.port.in.CreateConnectorCommand;
import io.github.khaledshawki.eoc.connectormanagement.application.port.in.CreateConnectorUseCase;
import io.github.khaledshawki.eoc.connectormanagement.application.port.out.ConnectorAuthorizationPort;
import io.github.khaledshawki.eoc.connectormanagement.application.port.out.ConnectorRepository;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.Connector;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ConnectorEndpoint;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ConnectorName;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ConnectorTenantId;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ConnectorType;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.CredentialReference;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.SyncPolicy;
import java.util.Objects;

public final class CreateConnectorService implements CreateConnectorUseCase {

  private final ConnectorRepository connectorRepository;
  private final ConnectorAuthorizationPort connectorAuthorizationPort;

  public CreateConnectorService(
      ConnectorRepository connectorRepository,
      ConnectorAuthorizationPort connectorAuthorizationPort) {
    this.connectorRepository =
        Objects.requireNonNull(connectorRepository, "Connector repository cannot be null");
    this.connectorAuthorizationPort =
        Objects.requireNonNull(
            connectorAuthorizationPort, "Connector authorization port cannot be null");
  }

  @Override
  public ConnectorResult create(CreateConnectorCommand command) {
    Objects.requireNonNull(command, "Command cannot be null");

    ConnectorTenantId tenantId = ConnectorTenantId.of(command.tenantId());
    if (!connectorAuthorizationPort.hasPermission(
        command.actor(), tenantId, ConnectorPermission.ADMINISTER)) {
      throw new ConnectorAccessDeniedException(tenantId, ConnectorPermission.ADMINISTER);
    }

    Connector connector = createConnector(command, tenantId);

    if (connectorRepository.existsByTenantIdAndName(connector.tenantId(), connector.name())) {
      throw new ConnectorNameAlreadyExistsException(connector.tenantId(), connector.name());
    }

    return ConnectorResult.from(connectorRepository.save(connector));
  }

  private static Connector createConnector(
      CreateConnectorCommand command, ConnectorTenantId tenantId) {
    try {
      return Connector.create(
          tenantId,
          ConnectorName.of(command.name()),
          ConnectorType.of(command.type()),
          ConnectorEndpoint.of(command.endpoint()),
          CredentialReference.of(command.credentialReference()),
          new SyncPolicy(command.syncMode(), command.syncInterval()));
    } catch (IllegalArgumentException exception) {
      throw new InvalidConnectorConfigurationException(exception.getMessage(), exception);
    }
  }
}
