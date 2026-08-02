package io.github.khaledshawki.eoc.connectormanagement.application.model.datasource;

import java.util.Objects;
import java.util.regex.Pattern;

/** Sanitized, source-neutral failure information safe for application-level decisions. */
public record BusinessDataSourceFailure(Category category, String diagnosticCode) {

  public static final int MAX_DIAGNOSTIC_CODE_LENGTH = 63;

  private static final Pattern DIAGNOSTIC_CODE_FORMAT =
      Pattern.compile("^[a-z0-9]+(?:-[a-z0-9]+)*$");

  public BusinessDataSourceFailure {
    Objects.requireNonNull(category, "Business data source failure category cannot be null");
    diagnosticCode =
        SourceContractValidation.requiredText(
            diagnosticCode, "Business data source diagnostic code", MAX_DIAGNOSTIC_CODE_LENGTH);
    if (!DIAGNOSTIC_CODE_FORMAT.matcher(diagnosticCode).matches()) {
      throw new IllegalArgumentException(
          "Business data source diagnostic code has an invalid format");
    }
  }

  public boolean retryable() {
    return category.retryable();
  }

  public enum Category {
    AUTHENTICATION_FAILED(false),
    AUTHORIZATION_FAILED(false),
    SOURCE_UNAVAILABLE(true),
    TIMEOUT(true),
    RATE_LIMITED(true),
    INVALID_POSITION(false),
    SOURCE_CONTRACT_VIOLATION(false),
    UNEXPECTED_SOURCE_FAILURE(false);

    private final boolean retryable;

    Category(boolean retryable) {
      this.retryable = retryable;
    }

    public boolean retryable() {
      return retryable;
    }
  }
}
