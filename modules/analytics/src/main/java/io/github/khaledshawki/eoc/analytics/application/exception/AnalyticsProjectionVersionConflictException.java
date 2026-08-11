package io.github.khaledshawki.eoc.analytics.application.exception;

import java.util.UUID;

public final class AnalyticsProjectionVersionConflictException extends RuntimeException {

  private final String aggregateType;
  private final UUID aggregateId;
  private final long aggregateVersion;

  public AnalyticsProjectionVersionConflictException(
      String aggregateType, UUID aggregateId, long aggregateVersion, String detail) {
    super(
        "Analytics projection version conflict for "
            + aggregateType
            + " "
            + aggregateId
            + " at version "
            + aggregateVersion
            + ": "
            + detail);
    this.aggregateType = aggregateType;
    this.aggregateId = aggregateId;
    this.aggregateVersion = aggregateVersion;
  }

  public String aggregateType() {
    return aggregateType;
  }

  public UUID aggregateId() {
    return aggregateId;
  }

  public long aggregateVersion() {
    return aggregateVersion;
  }
}
