package io.github.khaledshawki.eoc.analytics.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record ProjectionCursor(UUID eventId, long aggregateVersion, Instant occurredAt) {

  public ProjectionCursor {
    Objects.requireNonNull(eventId, "Projection event id cannot be null");
    if (aggregateVersion < 1) {
      throw new IllegalArgumentException("Projection aggregate version must be positive");
    }
    Objects.requireNonNull(occurredAt, "Projection occurrence timestamp cannot be null");
  }
}
