package io.github.khaledshawki.eoc.analytics.application.exception;

public final class AnalyticsReadUnavailableException extends RuntimeException {

  public AnalyticsReadUnavailableException(Throwable cause) {
    super("Analytics read persistence is unavailable", cause);
  }
}
