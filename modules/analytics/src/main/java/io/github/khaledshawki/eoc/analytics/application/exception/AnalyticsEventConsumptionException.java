package io.github.khaledshawki.eoc.analytics.application.exception;

import java.util.Objects;
import java.util.regex.Pattern;

public final class AnalyticsEventConsumptionException extends RuntimeException {

  private static final Pattern FAILURE_CODE = Pattern.compile("[a-z0-9]+(?:-[a-z0-9]+)*");

  private final String failureCode;
  private final boolean retryable;

  public AnalyticsEventConsumptionException(
      String failureCode, boolean retryable, Throwable cause) {
    super(requireFailureCode(failureCode), cause);
    this.failureCode = failureCode;
    this.retryable = retryable;
  }

  public String failureCode() {
    return failureCode;
  }

  public boolean retryable() {
    return retryable;
  }

  private static String requireFailureCode(String value) {
    Objects.requireNonNull(value, "Analytics event failure code cannot be null");
    if (value.length() > 128 || !FAILURE_CODE.matcher(value).matches()) {
      throw new IllegalArgumentException(
          "Analytics event failure code must be a bounded lower-case code");
    }
    return value;
  }
}
