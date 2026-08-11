package io.github.khaledshawki.eoc.operations.application.model.outbox;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record OperationsOutboxRecovery(
    UUID recoveryId,
    UUID eventId,
    int recoveryGeneration,
    String requestedByIssuer,
    String requestedBySubject,
    String reason,
    OperationsOutboxStatus previousStatus,
    int previousPublicationAttemptCount,
    int previousGenerationAttemptCount,
    String previousFailureCode,
    Instant requestedAt,
    Instant completedAt) {

  public OperationsOutboxRecovery {
    Objects.requireNonNull(recoveryId, "Operations outbox recovery id cannot be null");
    Objects.requireNonNull(eventId, "Operations outbox recovery event id cannot be null");
    if (recoveryGeneration < 1) {
      throw new IllegalArgumentException("Operations outbox recovery generation must be positive");
    }
    requestedByIssuer = requireText(requestedByIssuer, "Operations outbox recovery issuer");
    requestedBySubject = requireText(requestedBySubject, "Operations outbox recovery subject");
    reason = requireText(reason, "Operations outbox recovery reason");
    if (reason.length() > 500) {
      throw new IllegalArgumentException(
          "Operations outbox recovery reason cannot exceed 500 characters");
    }
    Objects.requireNonNull(
        previousStatus, "Operations outbox recovery previous status cannot be null");
    if (previousStatus != OperationsOutboxStatus.FAILED) {
      throw new IllegalArgumentException("Operations outbox recovery must originate from FAILED");
    }
    if (previousPublicationAttemptCount < 1) {
      throw new IllegalArgumentException("Previous publication attempts must be positive");
    }
    if (previousGenerationAttemptCount < 1
        || previousGenerationAttemptCount > previousPublicationAttemptCount) {
      throw new IllegalArgumentException("Previous generation attempts are inconsistent");
    }
    previousFailureCode =
        requireText(previousFailureCode, "Operations outbox previous failure code");
    Objects.requireNonNull(
        requestedAt, "Operations outbox recovery request timestamp cannot be null");
    Objects.requireNonNull(
        completedAt, "Operations outbox recovery completion timestamp cannot be null");
    if (completedAt.isBefore(requestedAt)) {
      throw new IllegalArgumentException(
          "Operations outbox recovery completion cannot precede its request");
    }
  }

  private static String requireText(String value, String label) {
    Objects.requireNonNull(value, label + " cannot be null");
    String normalized = value.strip();
    if (normalized.isEmpty()) {
      throw new IllegalArgumentException(label + " cannot be blank");
    }
    return normalized;
  }
}
