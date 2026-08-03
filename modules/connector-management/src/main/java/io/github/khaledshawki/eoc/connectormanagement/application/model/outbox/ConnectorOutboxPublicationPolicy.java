package io.github.khaledshawki.eoc.connectormanagement.application.model.outbox;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

public record ConnectorOutboxPublicationPolicy(int maxAttempts, Duration retryDelay) {

  public ConnectorOutboxPublicationPolicy {
    Objects.requireNonNull(retryDelay, "Publication retry delay cannot be null");
    if (maxAttempts < 1 || maxAttempts > 100) {
      throw new IllegalArgumentException("Publication max attempts must be between 1 and 100");
    }
    if (retryDelay.isZero()
        || retryDelay.isNegative()
        || retryDelay.compareTo(Duration.ofDays(1)) > 0) {
      throw new IllegalArgumentException(
          "Publication retry delay must be positive and at most one day");
    }
  }

  public Instant nextRetryAt(Instant now) {
    Objects.requireNonNull(now, "Publication retry base timestamp cannot be null");
    return now.plus(retryDelay);
  }
}
