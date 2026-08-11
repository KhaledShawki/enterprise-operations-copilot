package io.github.khaledshawki.eoc.connectormanagement.application.model.recovery;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record ConnectorDeadLetterReplayRequest(
    UUID requestId,
    ConnectorDeadLetterReference deadLetter,
    ConnectorDeadLetterReplayStatus status,
    int replayGeneration,
    String requestedByIssuer,
    String requestedBySubject,
    String reason,
    Instant requestedAt,
    int publicationAttemptCount,
    Optional<String> lastFailureCode,
    Optional<Instant> replayedAt) {

  public ConnectorDeadLetterReplayRequest {
    Objects.requireNonNull(requestId, "Replay request id cannot be null");
    Objects.requireNonNull(deadLetter, "Replay request dead-letter reference cannot be null");
    Objects.requireNonNull(status, "Replay request status cannot be null");
    if (replayGeneration < 1 || replayGeneration > 100) {
      throw new IllegalArgumentException("Replay request generation is invalid");
    }
    requestedByIssuer = requireText(requestedByIssuer, "Replay requester issuer", 500);
    requestedBySubject = requireText(requestedBySubject, "Replay requester subject", 255);
    reason = requireText(reason, "Replay reason", 500);
    Objects.requireNonNull(requestedAt, "Replay request timestamp cannot be null");
    if (publicationAttemptCount < 0) {
      throw new IllegalArgumentException("Replay publication attempt count cannot be negative");
    }
    Objects.requireNonNull(lastFailureCode, "Replay failure code optional cannot be null");
    Objects.requireNonNull(replayedAt, "Replay timestamp optional cannot be null");
  }

  static String requireText(String value, String description, int maxLength) {
    Objects.requireNonNull(value, description + " cannot be null");
    if (value.isBlank() || value.length() > maxLength) {
      throw new IllegalArgumentException(description + " is invalid");
    }
    return value;
  }
}
