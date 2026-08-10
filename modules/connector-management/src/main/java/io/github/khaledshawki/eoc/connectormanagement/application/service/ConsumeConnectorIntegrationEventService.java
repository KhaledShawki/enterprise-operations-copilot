package io.github.khaledshawki.eoc.connectormanagement.application.service;

import io.github.khaledshawki.eoc.connectormanagement.application.model.event.ConnectorIntegrationEventEnvelope;
import io.github.khaledshawki.eoc.connectormanagement.application.port.in.ConsumeConnectorIntegrationEventUseCase;
import io.github.khaledshawki.eoc.connectormanagement.application.port.out.ConnectorIntegrationEventInbox;
import java.util.Objects;

public final class ConsumeConnectorIntegrationEventService
    implements ConsumeConnectorIntegrationEventUseCase {

  private final ConnectorIntegrationEventInbox inbox;

  public ConsumeConnectorIntegrationEventService(ConnectorIntegrationEventInbox inbox) {
    this.inbox = Objects.requireNonNull(inbox, "Connector integration event inbox cannot be null");
  }

  @Override
  public void consume(ConnectorIntegrationEventEnvelope event) {
    inbox.consume(Objects.requireNonNull(event, "Connector integration event cannot be null"));
  }
}
