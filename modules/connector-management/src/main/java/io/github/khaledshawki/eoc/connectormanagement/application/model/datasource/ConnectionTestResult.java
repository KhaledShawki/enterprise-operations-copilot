package io.github.khaledshawki.eoc.connectormanagement.application.model.datasource;

import java.util.Objects;
import java.util.Optional;

/** Sanitized outcome of testing connectivity and authentication against a business data source. */
public record ConnectionTestResult(Status status, Optional<BusinessDataSourceFailure> failure) {

  public ConnectionTestResult {
    Objects.requireNonNull(status, "Connection test status cannot be null");
    Objects.requireNonNull(failure, "Connection test failure cannot be null");
    if (status == Status.CONNECTED && failure.isPresent()) {
      throw new IllegalArgumentException("A successful connection test cannot contain a failure");
    }
    if (status == Status.FAILED && failure.isEmpty()) {
      throw new IllegalArgumentException("A failed connection test requires a failure");
    }
  }

  public static ConnectionTestResult connected() {
    return new ConnectionTestResult(Status.CONNECTED, Optional.empty());
  }

  public static ConnectionTestResult failed(BusinessDataSourceFailure failure) {
    return new ConnectionTestResult(Status.FAILED, Optional.of(failure));
  }

  public enum Status {
    CONNECTED,
    FAILED
  }
}
