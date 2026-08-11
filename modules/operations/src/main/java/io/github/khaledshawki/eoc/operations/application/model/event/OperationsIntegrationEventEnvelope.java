package io.github.khaledshawki.eoc.operations.application.model.event;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/** Broker-neutral Operations integration-event envelope. */
public record OperationsIntegrationEventEnvelope(
    UUID eventId,
    String eventType,
    int schemaVersion,
    UUID tenantId,
    String aggregateType,
    UUID aggregateId,
    long aggregateVersion,
    String payload,
    Instant occurredAt) {

  private static final Pattern EVENT_TYPE = Pattern.compile("[a-z0-9]+(?:[.-][a-z0-9]+)*");
  private static final Pattern AGGREGATE_TYPE = Pattern.compile("[A-Z][A-Z0-9_]{0,63}");

  public OperationsIntegrationEventEnvelope {
    Objects.requireNonNull(eventId, "Operations event id cannot be null");
    eventType = requireEventType(eventType);
    if (schemaVersion < 1) {
      throw new IllegalArgumentException("Operations event schema version must be positive");
    }
    Objects.requireNonNull(tenantId, "Operations event tenant id cannot be null");
    aggregateType = requireAggregateType(aggregateType);
    Objects.requireNonNull(aggregateId, "Operations event aggregate id cannot be null");
    if (aggregateVersion < 1) {
      throw new IllegalArgumentException("Operations event aggregate version must be positive");
    }
    payload = requirePayload(payload);
    Objects.requireNonNull(occurredAt, "Operations event occurrence timestamp cannot be null");
  }

  private static String requireEventType(String value) {
    Objects.requireNonNull(value, "Operations event type cannot be null");
    if (value.length() > 128 || !EVENT_TYPE.matcher(value).matches()) {
      throw new IllegalArgumentException("Operations event type must be a bounded versioned code");
    }
    return value;
  }

  private static String requireAggregateType(String value) {
    Objects.requireNonNull(value, "Operations event aggregate type cannot be null");
    if (!AGGREGATE_TYPE.matcher(value).matches()) {
      throw new IllegalArgumentException(
          "Operations event aggregate type must be an uppercase contract code");
    }
    return value;
  }

  private static String requirePayload(String value) {
    Objects.requireNonNull(value, "Operations event payload cannot be null");
    if (value.isBlank()) {
      throw new IllegalArgumentException("Operations event payload cannot be blank");
    }
    return value;
  }
}
