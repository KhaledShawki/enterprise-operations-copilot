package io.github.khaledshawki.eoc.connectormanagement.application.port.in;

import io.github.khaledshawki.eoc.connectormanagement.application.model.event.ConnectorIntegrationEventEnvelope;

@FunctionalInterface
public interface ConsumeConnectorIntegrationEventUseCase {

  void consume(ConnectorIntegrationEventEnvelope event);
}
