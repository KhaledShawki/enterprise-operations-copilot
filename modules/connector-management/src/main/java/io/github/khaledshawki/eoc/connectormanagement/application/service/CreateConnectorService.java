package io.github.khaledshawki.eoc.connectormanagement.application.service;

import io.github.khaledshawki.eoc.connectormanagement.application.exception.ConnectorNameAlreadyExistsException;
import io.github.khaledshawki.eoc.connectormanagement.application.exception.InvalidConnectorConfigurationException;
import io.github.khaledshawki.eoc.connectormanagement.application.port.in.ConnectorResult;
import io.github.khaledshawki.eoc.connectormanagement.application.port.in.CreateConnectorCommand;
import io.github.khaledshawki.eoc.connectormanagement.application.port.in.CreateConnectorUseCase;
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

  public CreateConnectorService(ConnectorRepository connectorRepository) {
    this.connectorRepository =
        Objects.requireNonNull(connectorRepository, "Connector repository cannot be null");
  }

  @Override
  public ConnectorResult create(CreateConnectorCommand command) {
    Objects.requireNonNull(command, "Command cannot be null");

    Connector connector = createConnector(command);

    if (connectorRepository.existsByTenantIdAndName(connector.tenantId(), connector.name())) {
      throw new ConnectorNameAlreadyExistsException(connector.tenantId(), connector.name());
    }

    return ConnectorResult.from(connectorRepository.save(connector));
  }

  private static Connector createConnector(CreateConnectorCommand command) {
    try {
      return Connector.create(
          ConnectorTenantId.of(command.tenantId()),
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
