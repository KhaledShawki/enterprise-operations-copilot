package io.github.khaledshawki.eoc.connectormanagement.application.model.recovery;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

public record ConnectorDeadLetterReplayClaim(
    String workerId, int batchSize, Instant claimedAt, Duration claimLease) {

  public ConnectorDeadLetterReplayClaim {
    workerId = ConnectorDeadLetterReplayRequest.requireText(workerId, "Replay worker id", 128);
    if (batchSize < 1 || batchSize > 100) {
      throw new IllegalArgumentException("Replay batch size must be between 1 and 100");
    }
    Objects.requireNonNull(claimedAt, "Replay claim timestamp cannot be null");
    Objects.requireNonNull(claimLease, "Replay claim lease cannot be null");
    if (claimLease.isZero()
        || claimLease.isNegative()
        || claimLease.compareTo(Duration.ofDays(1)) > 0) {
      throw new IllegalArgumentException("Replay claim lease must be positive and at most one day");
    }
  }

  public Instant staleBefore() {
    return claimedAt.minus(claimLease);
  }
}
