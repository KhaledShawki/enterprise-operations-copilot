package io.github.khaledshawki.eoc.connectormanagement.application.port.in;

public interface GetConnectorUseCase {

  ConnectorResult get(GetConnectorQuery query);
}
