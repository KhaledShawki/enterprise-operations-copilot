package io.github.khaledshawki.eoc.operations.application.port.out;

import io.github.khaledshawki.eoc.operations.application.model.event.OperationsIntegrationEvent;
import io.github.khaledshawki.eoc.operations.application.model.event.PendingOperationsIntegrationEvent;

/** Atomically allocates aggregate version and persists one immutable Operations event. */
@FunctionalInterface
public interface OperationsIntegrationEventOutbox {

  OperationsIntegrationEvent append(PendingOperationsIntegrationEvent event);
}
