package io.github.khaledshawki.eoc.connectormanagement.application.model.recovery;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record ConnectorDeadLetterReplayRetry(
    UUID requestId,
    String claimOwner,
    int publicationAttempt,
    String failureCode,
    Instant nextAttemptAt,
    Instant recordedAt) {

  public ConnectorDeadLetterReplayRetry {
    Objects.requireNonNull(requestId, "Replay retry request id cannot be null");
    claimOwner =
        ConnectorDeadLetterReplayRequest.requireText(claimOwner, "Replay claim owner", 128);
    if (publicationAttempt < 1) {
      throw new IllegalArgumentException("Replay publication attempt must be positive");
    }
    failureCode =
        ConnectorDeadLetterReplayRequest.requireText(
            failureCode, "Replay publication failure code", 160);
    Objects.requireNonNull(nextAttemptAt, "Replay next-attempt timestamp cannot be null");
    Objects.requireNonNull(recordedAt, "Replay retry timestamp cannot be null");
    if (!nextAttemptAt.isAfter(recordedAt)) {
      throw new IllegalArgumentException("Replay next-attempt timestamp must be in the future");
    }
  }
}
