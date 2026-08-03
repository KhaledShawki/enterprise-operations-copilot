package io.github.khaledshawki.eoc.connectormanagement.application.model.event;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record ConnectorIntegrationEvent(
    UUID eventId,
    ConnectorIntegrationEventType type,
    UUID tenantId,
    String aggregateType,
    UUID aggregateId,
    Instant occurredAt,
    ConnectorIntegrationEventPayload payload) {

  public ConnectorIntegrationEvent {
    Objects.requireNonNull(eventId, "Event id cannot be null");
    Objects.requireNonNull(type, "Event type cannot be null");
    Objects.requireNonNull(tenantId, "Event tenant id cannot be null");
    Objects.requireNonNull(aggregateType, "Aggregate type cannot be null");
    Objects.requireNonNull(aggregateId, "Aggregate id cannot be null");
    Objects.requireNonNull(occurredAt, "Event occurrence timestamp cannot be null");
    Objects.requireNonNull(payload, "Event payload cannot be null");

    if (!type.aggregateType().equals(aggregateType)) {
      throw new IllegalArgumentException("Aggregate type does not match the event contract");
    }
    if (!type.supports(payload)) {
      throw new IllegalArgumentException("Payload type does not match the event contract");
    }
    if (payload instanceof ImportRunRetryScheduledPayload retry
        && !retry.nextRetryAt().isAfter(occurredAt)) {
      throw new IllegalArgumentException("Retry timestamp must be after event occurrence");
    }
  }

  public String eventType() {
    return type.eventType();
  }

  public int schemaVersion() {
    return type.schemaVersion();
  }
}
