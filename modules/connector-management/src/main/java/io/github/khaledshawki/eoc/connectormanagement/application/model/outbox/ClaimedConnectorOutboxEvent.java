package io.github.khaledshawki.eoc.connectormanagement.application.model.outbox;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

public record ClaimedConnectorOutboxEvent(
    UUID eventId,
    String eventType,
    int schemaVersion,
    UUID tenantId,
    String aggregateType,
    UUID aggregateId,
    String payload,
    Instant occurredAt,
    int publicationAttempt,
    String claimOwner,
    Instant claimedAt) {

  private static final Pattern EVENT_TYPE = Pattern.compile("[a-z0-9]+(?:[.-][a-z0-9]+)*");
  private static final Pattern AGGREGATE_TYPE = Pattern.compile("[A-Z][A-Z0-9_]{0,63}");

  public ClaimedConnectorOutboxEvent {
    Objects.requireNonNull(eventId, "Outbox event id cannot be null");
    eventType = requireEventType(eventType);
    if (schemaVersion < 1) {
      throw new IllegalArgumentException("Outbox schema version must be positive");
    }
    Objects.requireNonNull(tenantId, "Outbox tenant id cannot be null");
    aggregateType = requireAggregateType(aggregateType);
    Objects.requireNonNull(aggregateId, "Outbox aggregate id cannot be null");
    payload = requirePayload(payload);
    Objects.requireNonNull(occurredAt, "Outbox occurrence timestamp cannot be null");
    if (publicationAttempt < 1) {
      throw new IllegalArgumentException("Publication attempt must be positive");
    }
    claimOwner = ConnectorOutboxClaim.requireClaimOwner(claimOwner);
    Objects.requireNonNull(claimedAt, "Claim timestamp cannot be null");
    if (claimedAt.isBefore(occurredAt)) {
      throw new IllegalArgumentException("Claim timestamp cannot precede event occurrence");
    }
  }

  private static String requireEventType(String value) {
    Objects.requireNonNull(value, "Outbox event type cannot be null");
    if (value.length() > 128 || !EVENT_TYPE.matcher(value).matches()) {
      throw new IllegalArgumentException("Outbox event type must be a bounded versioned code");
    }
    return value;
  }

  private static String requireAggregateType(String value) {
    Objects.requireNonNull(value, "Outbox aggregate type cannot be null");
    if (!AGGREGATE_TYPE.matcher(value).matches()) {
      throw new IllegalArgumentException(
          "Outbox aggregate type must be an uppercase contract code");
    }
    return value;
  }

  private static String requirePayload(String value) {
    Objects.requireNonNull(value, "Outbox payload cannot be null");
    if (value.isBlank()) {
      throw new IllegalArgumentException("Outbox payload cannot be blank");
    }
    return value;
  }
}
