package io.github.khaledshawki.eoc.connectormanagement.application.port.out;

import io.github.khaledshawki.eoc.connectormanagement.application.model.event.ConnectorIntegrationEventEnvelope;

@FunctionalInterface
public interface ConnectorIntegrationEventPublisher {

  /**
   * Publishes one broker-neutral event using at-least-once delivery semantics.
   *
   * <p>Implementations must return only after the selected transport has acknowledged the send or
   * throw a {@code ConnectorEventPublicationException}. Consumers must use the stable event id for
   * idempotency because an uncertain acknowledgement or an expired outbox claim can cause replay.
   */
  void publish(ConnectorIntegrationEventEnvelope event);
}
