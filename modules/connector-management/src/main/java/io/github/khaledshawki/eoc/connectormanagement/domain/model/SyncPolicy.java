package io.github.khaledshawki.eoc.connectormanagement.domain.model;

import java.time.Duration;
import java.util.Objects;

public record SyncPolicy(Mode mode, Duration interval) {

  public enum Mode {
    MANUAL,
    SCHEDULED
  }

  public SyncPolicy {
    Objects.requireNonNull(mode, "Sync policy mode cannot be null");
    Objects.requireNonNull(interval, "Sync policy interval cannot be null");

    if (mode == Mode.MANUAL && !interval.isZero()) {
      throw new IllegalArgumentException("Manual sync policy interval must be zero");
    }

    if (mode == Mode.SCHEDULED && (interval.isZero() || interval.isNegative())) {
      throw new IllegalArgumentException("Scheduled sync policy interval must be positive");
    }
  }

  public static SyncPolicy manual() {
    return new SyncPolicy(Mode.MANUAL, Duration.ZERO);
  }

  public static SyncPolicy scheduled(Duration interval) {
    return new SyncPolicy(Mode.SCHEDULED, interval);
  }
}
