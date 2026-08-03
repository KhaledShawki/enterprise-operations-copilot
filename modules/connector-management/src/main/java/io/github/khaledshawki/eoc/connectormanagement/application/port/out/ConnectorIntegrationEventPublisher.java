package io.github.khaledshawki.eoc.connectormanagement.application.port.out;

import io.github.khaledshawki.eoc.connectormanagement.application.model.outbox.ClaimedConnectorOutboxEvent;

@FunctionalInterface
public interface ConnectorIntegrationEventPublisher {

  /**
   * Publishes one event using at-least-once delivery semantics. Implementations and consumers must
   * use the stable event id for idempotency because a crash can cause replay after the claim lease
   * expires.
   */
  void publish(ClaimedConnectorOutboxEvent event);
}
