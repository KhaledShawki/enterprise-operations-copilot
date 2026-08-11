package io.github.khaledshawki.eoc.operations.application.model.event;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Operations event content before durable event identity and aggregate version allocation. */
public record PendingOperationsIntegrationEvent(
    OperationsIntegrationEventType type,
    UUID tenantId,
    Instant occurredAt,
    OperationsIntegrationEventPayload payload) {

  public PendingOperationsIntegrationEvent {
    Objects.requireNonNull(type, "Pending Operations event type cannot be null");
    Objects.requireNonNull(tenantId, "Pending Operations event tenant id cannot be null");
    Objects.requireNonNull(
        occurredAt, "Pending Operations event occurrence timestamp cannot be null");
    Objects.requireNonNull(payload, "Pending Operations event payload cannot be null");
    if (!type.supports(payload)) {
      throw new IllegalArgumentException(
          "Pending payload type does not match the Operations event contract");
    }
  }

  public String aggregateType() {
    return type.aggregateType();
  }

  public UUID aggregateId() {
    return payload.aggregateId();
  }

  public OperationsIntegrationEvent materialize(UUID eventId, long aggregateVersion) {
    return new OperationsIntegrationEvent(
        eventId,
        type,
        tenantId,
        aggregateType(),
        aggregateId(),
        aggregateVersion,
        occurredAt,
        payload);
  }
}
