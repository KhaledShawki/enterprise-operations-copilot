package io.github.khaledshawki.eoc.operations.application.model.event;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record OperationsIntegrationEvent(
    UUID eventId,
    OperationsIntegrationEventType type,
    UUID tenantId,
    String aggregateType,
    UUID aggregateId,
    long aggregateVersion,
    Instant occurredAt,
    OperationsIntegrationEventPayload payload) {

  public OperationsIntegrationEvent {
    Objects.requireNonNull(eventId, "Operations event id cannot be null");
    Objects.requireNonNull(type, "Operations event type cannot be null");
    Objects.requireNonNull(tenantId, "Operations event tenant id cannot be null");
    Objects.requireNonNull(aggregateType, "Operations event aggregate type cannot be null");
    Objects.requireNonNull(aggregateId, "Operations event aggregate id cannot be null");
    Objects.requireNonNull(occurredAt, "Operations event occurrence timestamp cannot be null");
    Objects.requireNonNull(payload, "Operations event payload cannot be null");
    if (aggregateVersion < 1) {
      throw new IllegalArgumentException("Operations event aggregate version must be positive");
    }
    if (!type.aggregateType().equals(aggregateType)) {
      throw new IllegalArgumentException(
          "Aggregate type does not match the Operations event contract");
    }
    if (!type.supports(payload)) {
      throw new IllegalArgumentException(
          "Payload type does not match the Operations event contract");
    }
    if (!payload.aggregateId().equals(aggregateId)) {
      throw new IllegalArgumentException(
          "Payload aggregate id does not match the Operations event");
    }
  }

  public String eventType() {
    return type.eventType();
  }

  public int schemaVersion() {
    return type.schemaVersion();
  }
}
