package io.github.khaledshawki.eoc.connectormanagement.application.model.recovery;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record ClaimedConnectorDeadLetterReplay(
    UUID requestId,
    ConnectorDeadLetterReference deadLetter,
    String sourceTopic,
    int sourcePartition,
    long sourceOffset,
    Instant sourceTimestamp,
    Optional<String> key,
    Optional<String> value,
    List<ConnectorDeadLetterHeader> headers,
    int replayGeneration,
    int publicationAttempt,
    String claimOwner,
    Instant claimedAt) {

  public ClaimedConnectorDeadLetterReplay {
    Objects.requireNonNull(requestId, "Claimed replay request id cannot be null");
    Objects.requireNonNull(deadLetter, "Claimed replay dead-letter reference cannot be null");
    sourceTopic =
        ConnectorDeadLetterReplayRequest.requireText(sourceTopic, "Replay source topic", 249);
    if (sourcePartition < 0 || sourceOffset < 0) {
      throw new IllegalArgumentException("Replay source coordinates are invalid");
    }
    Objects.requireNonNull(sourceTimestamp, "Replay source timestamp cannot be null");
    Objects.requireNonNull(key, "Replay key optional cannot be null");
    Objects.requireNonNull(value, "Replay value optional cannot be null");
    headers = List.copyOf(headers);
    if (replayGeneration < 1 || replayGeneration > 100) {
      throw new IllegalArgumentException("Replay generation is invalid");
    }
    if (publicationAttempt < 1) {
      throw new IllegalArgumentException("Replay publication attempt must be positive");
    }
    claimOwner =
        ConnectorDeadLetterReplayRequest.requireText(claimOwner, "Replay claim owner", 128);
    Objects.requireNonNull(claimedAt, "Replay claim timestamp cannot be null");
  }
}
