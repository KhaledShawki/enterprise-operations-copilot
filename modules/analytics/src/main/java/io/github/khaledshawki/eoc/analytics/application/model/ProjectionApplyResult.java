package io.github.khaledshawki.eoc.analytics.application.model;

import java.util.Objects;
import java.util.UUID;

public record ProjectionApplyResult(
    UUID eventId, long aggregateVersion, ProjectionApplyStatus status) {

  public ProjectionApplyResult {
    Objects.requireNonNull(eventId, "Projection result event id cannot be null");
    if (aggregateVersion < 1) {
      throw new IllegalArgumentException("Projection result aggregate version must be positive");
    }
    Objects.requireNonNull(status, "Projection result status cannot be null");
  }

  public static ProjectionApplyResult applied(UUID eventId, long aggregateVersion) {
    return new ProjectionApplyResult(eventId, aggregateVersion, ProjectionApplyStatus.APPLIED);
  }

  public static ProjectionApplyResult duplicate(UUID eventId, long aggregateVersion) {
    return new ProjectionApplyResult(eventId, aggregateVersion, ProjectionApplyStatus.DUPLICATE);
  }
}
