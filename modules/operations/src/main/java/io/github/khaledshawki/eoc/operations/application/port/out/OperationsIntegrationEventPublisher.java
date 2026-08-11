package io.github.khaledshawki.eoc.operations.application.port.out;

import io.github.khaledshawki.eoc.operations.application.model.event.OperationsIntegrationEventEnvelope;

@FunctionalInterface
public interface OperationsIntegrationEventPublisher {

  /**
   * Publishes one broker-neutral event and returns only after transport acknowledgement.
   *
   * <p>Implementations must throw an {@code OperationsEventPublicationException} for classified
   * transport failures. A crash or uncertain acknowledgement can replay the stable event id.
   */
  void publish(OperationsIntegrationEventEnvelope event);
}
