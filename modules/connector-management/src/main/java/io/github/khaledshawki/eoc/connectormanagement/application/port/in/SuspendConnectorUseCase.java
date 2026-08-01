package io.github.khaledshawki.eoc.connectormanagement.application.port.in;

public interface SuspendConnectorUseCase {

  ConnectorResult suspend(SuspendConnectorCommand command);
}
