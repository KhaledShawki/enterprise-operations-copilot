package io.github.khaledshawki.eoc.analytics.application.model.event;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

public record AnalyticsIntegrationEvent(
    UUID eventId,
    String eventType,
    int schemaVersion,
    UUID tenantId,
    String aggregateType,
    UUID aggregateId,
    long aggregateVersion,
    String payload,
    Instant occurredAt,
    AnalyticsProjectionPayload projectionPayload) {

  private static final Pattern EVENT_TYPE = Pattern.compile("[a-z0-9]+(?:[.-][a-z0-9]+)*");
  private static final Pattern AGGREGATE_TYPE = Pattern.compile("[A-Z][A-Z0-9_]{0,63}");

  public AnalyticsIntegrationEvent {
    Objects.requireNonNull(eventId, "Analytics event id cannot be null");
    eventType = requireEventType(eventType);
    if (schemaVersion < 1) {
      throw new IllegalArgumentException("Analytics event schema version must be positive");
    }
    Objects.requireNonNull(tenantId, "Analytics event tenant id cannot be null");
    aggregateType = requireAggregateType(aggregateType);
    Objects.requireNonNull(aggregateId, "Analytics event aggregate id cannot be null");
    if (aggregateVersion < 1) {
      throw new IllegalArgumentException("Analytics event aggregate version must be positive");
    }
    payload = requirePayload(payload);
    Objects.requireNonNull(occurredAt, "Analytics event occurrence timestamp cannot be null");
    Objects.requireNonNull(projectionPayload, "Analytics projection payload cannot be null");
  }

  private static String requireEventType(String value) {
    Objects.requireNonNull(value, "Analytics event type cannot be null");
    if (value.length() > 128 || !EVENT_TYPE.matcher(value).matches()) {
      throw new IllegalArgumentException("Analytics event type must be a bounded versioned code");
    }
    return value;
  }

  private static String requireAggregateType(String value) {
    Objects.requireNonNull(value, "Analytics event aggregate type cannot be null");
    if (!AGGREGATE_TYPE.matcher(value).matches()) {
      throw new IllegalArgumentException(
          "Analytics event aggregate type must be an uppercase contract code");
    }
    return value;
  }

  private static String requirePayload(String value) {
    Objects.requireNonNull(value, "Analytics event payload cannot be null");
    if (value.isBlank()) {
      throw new IllegalArgumentException("Analytics event payload cannot be blank");
    }
    return value;
  }
}
