package io.github.khaledshawki.eoc.connectormanagement.application.port.in;

public interface CreateConnectorUseCase {

  ConnectorResult create(CreateConnectorCommand command);
}
