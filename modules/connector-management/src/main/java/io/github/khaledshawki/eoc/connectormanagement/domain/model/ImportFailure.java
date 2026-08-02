package io.github.khaledshawki.eoc.connectormanagement.domain.model;

import java.util.Objects;
import java.util.regex.Pattern;

/** Sanitized failure information safe to persist and expose to operators. */
public record ImportFailure(ImportFailureCategory category, String diagnosticCode) {

  public static final int MAX_DIAGNOSTIC_CODE_LENGTH = 63;

  private static final Pattern DIAGNOSTIC_CODE_FORMAT =
      Pattern.compile("^[a-z0-9]+(?:-[a-z0-9]+)*$");

  public ImportFailure {
    Objects.requireNonNull(category, "Import failure category cannot be null");
    Objects.requireNonNull(diagnosticCode, "Import failure diagnostic code cannot be null");
    diagnosticCode = diagnosticCode.trim();
    if (diagnosticCode.isEmpty()) {
      throw new IllegalArgumentException("Import failure diagnostic code cannot be empty");
    }
    if (diagnosticCode.length() > MAX_DIAGNOSTIC_CODE_LENGTH) {
      throw new IllegalArgumentException(
          "Import failure diagnostic code cannot be longer than "
              + MAX_DIAGNOSTIC_CODE_LENGTH
              + " characters");
    }
    if (!DIAGNOSTIC_CODE_FORMAT.matcher(diagnosticCode).matches()) {
      throw new IllegalArgumentException("Import failure diagnostic code has an invalid format");
    }
  }

  public boolean retryable() {
    return category.retryable();
  }
}
