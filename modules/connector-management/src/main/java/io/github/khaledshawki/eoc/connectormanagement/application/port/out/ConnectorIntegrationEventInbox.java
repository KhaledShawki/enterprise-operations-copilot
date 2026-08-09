package io.github.khaledshawki.eoc.connectormanagement.application.port.out;

import io.github.khaledshawki.eoc.connectormanagement.application.model.event.ConnectorIntegrationEventEnvelope;

@FunctionalInterface
public interface ConnectorIntegrationEventInbox {

  /**
   * Consumes one integration event with event-id-based idempotency.
   *
   * <p>Implementations must reject an event-id collision when the same id is reused for different
   * immutable event content.
   */
  void consume(ConnectorIntegrationEventEnvelope event);
}
