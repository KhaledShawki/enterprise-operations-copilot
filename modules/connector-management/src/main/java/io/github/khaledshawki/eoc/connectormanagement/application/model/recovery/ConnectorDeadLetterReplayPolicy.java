package io.github.khaledshawki.eoc.connectormanagement.application.model.recovery;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

public record ConnectorDeadLetterReplayPolicy(int maxAttempts, Duration retryDelay) {

  public ConnectorDeadLetterReplayPolicy {
    if (maxAttempts < 1 || maxAttempts > 100) {
      throw new IllegalArgumentException("Replay max attempts must be between 1 and 100");
    }
    Objects.requireNonNull(retryDelay, "Replay retry delay cannot be null");
    if (retryDelay.isZero()
        || retryDelay.isNegative()
        || retryDelay.compareTo(Duration.ofDays(1)) > 0) {
      throw new IllegalArgumentException("Replay retry delay must be positive and at most one day");
    }
  }

  public Instant nextAttemptAt(Instant now) {
    return Objects.requireNonNull(now, "Replay retry timestamp cannot be null").plus(retryDelay);
  }
}
