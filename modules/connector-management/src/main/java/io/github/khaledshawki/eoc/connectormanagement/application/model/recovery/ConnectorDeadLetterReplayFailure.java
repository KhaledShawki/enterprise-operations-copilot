package io.github.khaledshawki.eoc.connectormanagement.application.model.recovery;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record ConnectorDeadLetterReplayFailure(
    UUID requestId,
    String claimOwner,
    int publicationAttempt,
    String failureCode,
    Instant recordedAt) {

  public ConnectorDeadLetterReplayFailure {
    Objects.requireNonNull(requestId, "Replay failure request id cannot be null");
    claimOwner =
        ConnectorDeadLetterReplayRequest.requireText(claimOwner, "Replay claim owner", 128);
    if (publicationAttempt < 1) {
      throw new IllegalArgumentException("Replay publication attempt must be positive");
    }
    failureCode =
        ConnectorDeadLetterReplayRequest.requireText(
            failureCode, "Replay publication failure code", 160);
    Objects.requireNonNull(recordedAt, "Replay failure timestamp cannot be null");
  }
}
