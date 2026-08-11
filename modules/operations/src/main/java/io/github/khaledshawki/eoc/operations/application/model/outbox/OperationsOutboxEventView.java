package io.github.khaledshawki.eoc.operations.application.model.outbox;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record OperationsOutboxEventView(
    UUID eventId,
    String eventType,
    int schemaVersion,
    UUID tenantId,
    String aggregateType,
    UUID aggregateId,
    long aggregateVersion,
    Instant occurredAt,
    OperationsOutboxStatus status,
    int publicationAttemptCount,
    int recoveryGeneration,
    int generationAttemptCount,
    Instant nextPublishAt,
    Optional<Instant> claimedAt,
    Optional<String> claimedBy,
    Optional<Instant> publishedAt,
    Optional<String> lastFailureCode,
    Instant createdAt,
    Instant updatedAt) {

  public OperationsOutboxEventView {
    Objects.requireNonNull(eventId, "Operations outbox event id cannot be null");
    eventType = requireText(eventType, "Operations outbox event type");
    if (schemaVersion < 1) {
      throw new IllegalArgumentException("Operations outbox schema version must be positive");
    }
    Objects.requireNonNull(tenantId, "Operations outbox tenant id cannot be null");
    aggregateType = requireText(aggregateType, "Operations outbox aggregate type");
    Objects.requireNonNull(aggregateId, "Operations outbox aggregate id cannot be null");
    if (aggregateVersion < 1) {
      throw new IllegalArgumentException("Operations outbox aggregate version must be positive");
    }
    Objects.requireNonNull(occurredAt, "Operations outbox occurrence timestamp cannot be null");
    Objects.requireNonNull(status, "Operations outbox status cannot be null");
    if (publicationAttemptCount < 0) {
      throw new IllegalArgumentException(
          "Operations outbox publication attempts cannot be negative");
    }
    if (recoveryGeneration < 0) {
      throw new IllegalArgumentException(
          "Operations outbox recovery generation cannot be negative");
    }
    if (generationAttemptCount < 0 || generationAttemptCount > publicationAttemptCount) {
      throw new IllegalArgumentException(
          "Operations outbox generation attempts must be between zero and lifetime attempts");
    }
    Objects.requireNonNull(
        nextPublishAt, "Operations outbox next publish timestamp cannot be null");
    Objects.requireNonNull(claimedAt, "Operations outbox claimed timestamp cannot be null");
    Objects.requireNonNull(claimedBy, "Operations outbox claim owner cannot be null");
    claimedBy = claimedBy.map(value -> requireText(value, "Operations outbox claim owner"));
    Objects.requireNonNull(publishedAt, "Operations outbox published timestamp cannot be null");
    Objects.requireNonNull(lastFailureCode, "Operations outbox failure code cannot be null");
    lastFailureCode =
        lastFailureCode.map(value -> requireText(value, "Operations outbox failure code"));
    Objects.requireNonNull(createdAt, "Operations outbox created timestamp cannot be null");
    Objects.requireNonNull(updatedAt, "Operations outbox updated timestamp cannot be null");
    if (createdAt.isBefore(occurredAt) || updatedAt.isBefore(createdAt)) {
      throw new IllegalArgumentException("Operations outbox timestamps are inconsistent");
    }
    if (status == OperationsOutboxStatus.CLAIMED) {
      if (claimedAt.isEmpty() || claimedBy.isEmpty()) {
        throw new IllegalArgumentException(
            "Claimed Operations outbox events require claim evidence");
      }
    } else if (claimedAt.isPresent() || claimedBy.isPresent()) {
      throw new IllegalArgumentException(
          "Unclaimed Operations outbox events cannot retain claim evidence");
    }
    if ((status == OperationsOutboxStatus.PUBLISHED) != publishedAt.isPresent()) {
      throw new IllegalArgumentException("Operations outbox published state is inconsistent");
    }
    if (status == OperationsOutboxStatus.FAILED && lastFailureCode.isEmpty()) {
      throw new IllegalArgumentException(
          "Failed Operations outbox events require failure evidence");
    }
    if (status == OperationsOutboxStatus.PENDING
        && (publicationAttemptCount != 0
            || recoveryGeneration != 0
            || generationAttemptCount != 0
            || lastFailureCode.isPresent())) {
      throw new IllegalArgumentException("Pending Operations outbox state is inconsistent");
    }
  }

  private static String requireText(String value, String label) {
    Objects.requireNonNull(value, label + " cannot be null");
    if (value.isBlank()) {
      throw new IllegalArgumentException(label + " cannot be blank");
    }
    return value;
  }
}
