package io.github.khaledshawki.eoc.connectormanagement.application.port.in;

public interface ActivateConnectorUseCase {

  ConnectorResult activate(ActivateConnectorCommand command);
}
