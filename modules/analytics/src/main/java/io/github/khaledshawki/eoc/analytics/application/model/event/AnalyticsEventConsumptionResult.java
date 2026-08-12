package io.github.khaledshawki.eoc.analytics.application.model.event;

import java.util.Objects;
import java.util.UUID;

public record AnalyticsEventConsumptionResult(
    UUID eventId, long aggregateVersion, AnalyticsEventConsumptionStatus status) {

  public AnalyticsEventConsumptionResult {
    Objects.requireNonNull(eventId, "Analytics consumption event id cannot be null");
    if (aggregateVersion < 1) {
      throw new IllegalArgumentException(
          "Analytics consumption aggregate version must be positive");
    }
    Objects.requireNonNull(status, "Analytics consumption status cannot be null");
  }

  public static AnalyticsEventConsumptionResult applied(UUID eventId, long aggregateVersion) {
    return new AnalyticsEventConsumptionResult(
        eventId, aggregateVersion, AnalyticsEventConsumptionStatus.APPLIED);
  }

  public static AnalyticsEventConsumptionResult duplicate(UUID eventId, long aggregateVersion) {
    return new AnalyticsEventConsumptionResult(
        eventId, aggregateVersion, AnalyticsEventConsumptionStatus.DUPLICATE);
  }

  public static AnalyticsEventConsumptionResult ignored(UUID eventId, long aggregateVersion) {
    return new AnalyticsEventConsumptionResult(
        eventId, aggregateVersion, AnalyticsEventConsumptionStatus.IGNORED);
  }
}
