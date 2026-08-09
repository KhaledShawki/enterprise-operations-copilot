package io.github.khaledshawki.eoc.platform.connectormanagement.adapter.out.messaging.local;

import io.github.khaledshawki.eoc.connectormanagement.application.exception.ConnectorEventConsumptionException;
import io.github.khaledshawki.eoc.connectormanagement.application.exception.ConnectorEventPublicationException;
import io.github.khaledshawki.eoc.connectormanagement.application.model.event.ConnectorIntegrationEventEnvelope;
import io.github.khaledshawki.eoc.connectormanagement.application.port.out.ConnectorIntegrationEventInbox;
import io.github.khaledshawki.eoc.connectormanagement.application.port.out.ConnectorIntegrationEventPublisher;
import java.util.Objects;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
    name = "eoc.connector-events.transport",
    havingValue = "local",
    matchIfMissing = true)
final class LocalConnectorIntegrationEventPublisher implements ConnectorIntegrationEventPublisher {

  private final ConnectorIntegrationEventInbox inbox;

  LocalConnectorIntegrationEventPublisher(ConnectorIntegrationEventInbox inbox) {
    this.inbox = Objects.requireNonNull(inbox, "Connector integration event inbox cannot be null");
  }

  @Override
  public void publish(ConnectorIntegrationEventEnvelope event) {
    Objects.requireNonNull(event, "Connector integration event cannot be null");
    try {
      inbox.consume(event);
    } catch (ConnectorEventConsumptionException exception) {
      throw new ConnectorEventPublicationException(
          exception.failureCode(), exception.retryable(), exception);
    }
  }
}
