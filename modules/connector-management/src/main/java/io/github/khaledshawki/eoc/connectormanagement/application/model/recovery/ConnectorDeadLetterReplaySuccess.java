package io.github.khaledshawki.eoc.connectormanagement.application.model.recovery;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record ConnectorDeadLetterReplaySuccess(
    UUID requestId, String claimOwner, int publicationAttempt, Instant replayedAt) {

  public ConnectorDeadLetterReplaySuccess {
    Objects.requireNonNull(requestId, "Replay success request id cannot be null");
    claimOwner =
        ConnectorDeadLetterReplayRequest.requireText(claimOwner, "Replay claim owner", 128);
    if (publicationAttempt < 1) {
      throw new IllegalArgumentException("Replay publication attempt must be positive");
    }
    Objects.requireNonNull(replayedAt, "Replay success timestamp cannot be null");
  }
}
