package io.github.khaledshawki.eoc.connectormanagement.application.model.importing;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/** Bounded retry policy for one import run. A scheduler may execute the due retry later. */
public record ImportRetryPolicy(int maxAttempts, Duration retryDelay) {

  public ImportRetryPolicy {
    if (maxAttempts < 1) {
      throw new IllegalArgumentException("Maximum import attempts must be positive");
    }
    Objects.requireNonNull(retryDelay, "Import retry delay cannot be null");
    if (retryDelay.isZero() || retryDelay.isNegative()) {
      throw new IllegalArgumentException("Import retry delay must be positive");
    }
  }

  public boolean allowsAnotherAttempt(int completedAttempts) {
    return completedAttempts < maxAttempts;
  }

  public Instant nextRetryAt(Instant now) {
    Objects.requireNonNull(now, "Retry scheduling timestamp cannot be null");
    return now.plus(retryDelay);
  }
}
