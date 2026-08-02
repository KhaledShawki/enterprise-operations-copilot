package io.github.khaledshawki.eoc.connectormanagement.application.model.datasource;

import java.util.Objects;

/** Expected external-source failure raised while verifying or retrieving business data. */
public final class BusinessDataSourceException extends RuntimeException {

  private final BusinessDataSourceFailure failure;

  public BusinessDataSourceException(BusinessDataSourceFailure failure) {
    super(requireFailure(failure).diagnosticCode());
    this.failure = failure;
  }

  public BusinessDataSourceException(BusinessDataSourceFailure failure, Throwable cause) {
    super(requireFailure(failure).diagnosticCode(), cause);
    this.failure = failure;
  }

  public BusinessDataSourceFailure failure() {
    return failure;
  }

  private static BusinessDataSourceFailure requireFailure(BusinessDataSourceFailure failure) {
    return Objects.requireNonNull(failure, "Business data source failure cannot be null");
  }
}
