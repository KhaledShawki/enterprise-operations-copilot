package io.github.khaledshawki.eoc.analytics.application.exception;

public final class AnalyticsProjectionStateCorruptedException extends RuntimeException {

  public AnalyticsProjectionStateCorruptedException(String detail) {
    super("Analytics projection state is corrupted: " + detail);
  }
}
