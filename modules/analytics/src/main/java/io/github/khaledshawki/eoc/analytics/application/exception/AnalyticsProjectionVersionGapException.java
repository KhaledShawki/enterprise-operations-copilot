package io.github.khaledshawki.eoc.analytics.application.exception;

import java.util.UUID;

public final class AnalyticsProjectionVersionGapException extends RuntimeException {

  private final String aggregateType;
  private final UUID aggregateId;
  private final long expectedVersion;
  private final long actualVersion;

  public AnalyticsProjectionVersionGapException(
      String aggregateType, UUID aggregateId, long expectedVersion, long actualVersion) {
    super(
        "Analytics projection version gap for "
            + aggregateType
            + " "
            + aggregateId
            + ": expected "
            + expectedVersion
            + " but received "
            + actualVersion);
    this.aggregateType = aggregateType;
    this.aggregateId = aggregateId;
    this.expectedVersion = expectedVersion;
    this.actualVersion = actualVersion;
  }

  public String aggregateType() {
    return aggregateType;
  }

  public UUID aggregateId() {
    return aggregateId;
  }

  public long expectedVersion() {
    return expectedVersion;
  }

  public long actualVersion() {
    return actualVersion;
  }
}
