package io.github.khaledshawki.eoc.connectormanagement.application.model.datasource;

import java.util.Objects;
import java.util.Optional;

final class SourceContractValidation {

  private SourceContractValidation() {}

  static String requiredText(String value, String fieldName, int maximumLength) {
    Objects.requireNonNull(value, fieldName + " cannot be null");
    String normalizedValue = value.trim();
    if (normalizedValue.isEmpty()) {
      throw new IllegalArgumentException(fieldName + " cannot be empty");
    }
    if (normalizedValue.length() > maximumLength) {
      throw new IllegalArgumentException(
          fieldName + " cannot be longer than " + maximumLength + " characters");
    }
    return normalizedValue;
  }

  static Optional<String> optionalText(
      Optional<String> value, String fieldName, int maximumLength) {
    Objects.requireNonNull(value, fieldName + " cannot be null");
    return value.map(text -> requiredText(text, fieldName, maximumLength));
  }
}
