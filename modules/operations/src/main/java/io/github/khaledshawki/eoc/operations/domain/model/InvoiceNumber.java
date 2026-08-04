package io.github.khaledshawki.eoc.operations.domain.model;

import java.util.Objects;

public record InvoiceNumber(String value) {

  public static final int MAX_LENGTH = 100;

  public InvoiceNumber {
    Objects.requireNonNull(value, "Invoice number cannot be null");
    value = value.strip();
    if (value.isEmpty()) {
      throw new IllegalArgumentException("Invoice number cannot be blank");
    }
    if (value.length() > MAX_LENGTH) {
      throw new IllegalArgumentException(
          "Invoice number cannot exceed " + MAX_LENGTH + " characters");
    }
  }
}
