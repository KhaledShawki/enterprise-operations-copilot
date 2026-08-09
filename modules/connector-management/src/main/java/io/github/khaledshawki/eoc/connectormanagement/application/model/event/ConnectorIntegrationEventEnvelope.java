package io.github.khaledshawki.eoc.connectormanagement.application.model.event;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Broker-neutral integration-event envelope.
 *
 * <p>Publication claims, retry attempts, and lease metadata are deliberately excluded from this
 * contract. They are outbox implementation details and must never leak onto the event stream.
 */
public record ConnectorIntegrationEventEnvelope(
    UUID eventId,
    String eventType,
    int schemaVersion,
    UUID tenantId,
    String aggregateType,
    UUID aggregateId,
    String payload,
    Instant occurredAt) {

  private static final Pattern EVENT_TYPE = Pattern.compile("[a-z0-9]+(?:[.-][a-z0-9]+)*");
  private static final Pattern AGGREGATE_TYPE = Pattern.compile("[A-Z][A-Z0-9_]{0,63}");

  public ConnectorIntegrationEventEnvelope {
    Objects.requireNonNull(eventId, "Integration event id cannot be null");
    eventType = requireEventType(eventType);
    if (schemaVersion < 1) {
      throw new IllegalArgumentException("Integration event schema version must be positive");
    }
    Objects.requireNonNull(tenantId, "Integration event tenant id cannot be null");
    aggregateType = requireAggregateType(aggregateType);
    Objects.requireNonNull(aggregateId, "Integration event aggregate id cannot be null");
    payload = requirePayload(payload);
    Objects.requireNonNull(occurredAt, "Integration event occurrence timestamp cannot be null");
  }

  private static String requireEventType(String value) {
    Objects.requireNonNull(value, "Integration event type cannot be null");
    if (value.length() > 128 || !EVENT_TYPE.matcher(value).matches()) {
      throw new IllegalArgumentException("Integration event type must be a bounded versioned code");
    }
    return value;
  }

  private static String requireAggregateType(String value) {
    Objects.requireNonNull(value, "Integration event aggregate type cannot be null");
    if (!AGGREGATE_TYPE.matcher(value).matches()) {
      throw new IllegalArgumentException(
          "Integration event aggregate type must be an uppercase contract code");
    }
    return value;
  }

  private static String requirePayload(String value) {
    Objects.requireNonNull(value, "Integration event payload cannot be null");
    if (value.isBlank()) {
      throw new IllegalArgumentException("Integration event payload cannot be blank");
    }
    return value;
  }
}
