package io.github.khaledshawki.eoc.analytics.application.exception;

import java.util.UUID;

public final class AnalyticsProjectionVersionRegressionException extends RuntimeException {

  private final String aggregateType;
  private final UUID aggregateId;
  private final long currentVersion;
  private final long receivedVersion;

  public AnalyticsProjectionVersionRegressionException(
      String aggregateType, UUID aggregateId, long currentVersion, long receivedVersion) {
    super(
        "Analytics projection received an older "
            + aggregateType
            + " version for "
            + aggregateId
            + ": current "
            + currentVersion
            + ", received "
            + receivedVersion);
    this.aggregateType = aggregateType;
    this.aggregateId = aggregateId;
    this.currentVersion = currentVersion;
    this.receivedVersion = receivedVersion;
  }

  public String aggregateType() {
    return aggregateType;
  }

  public UUID aggregateId() {
    return aggregateId;
  }

  public long currentVersion() {
    return currentVersion;
  }

  public long receivedVersion() {
    return receivedVersion;
  }
}
