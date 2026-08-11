package io.github.khaledshawki.eoc.analytics.application.exception;

import java.util.UUID;

public final class AnalyticsProjectionConcurrentModificationException extends RuntimeException {

  private final String aggregateType;
  private final UUID aggregateId;
  private final long expectedCurrentVersion;

  public AnalyticsProjectionConcurrentModificationException(
      String aggregateType, UUID aggregateId, long expectedCurrentVersion) {
    super(
        "Analytics projection changed concurrently for "
            + aggregateType
            + " "
            + aggregateId
            + " while expecting current version "
            + expectedCurrentVersion);
    this.aggregateType = aggregateType;
    this.aggregateId = aggregateId;
    this.expectedCurrentVersion = expectedCurrentVersion;
  }

  public String aggregateType() {
    return aggregateType;
  }

  public UUID aggregateId() {
    return aggregateId;
  }

  public long expectedCurrentVersion() {
    return expectedCurrentVersion;
  }
}
