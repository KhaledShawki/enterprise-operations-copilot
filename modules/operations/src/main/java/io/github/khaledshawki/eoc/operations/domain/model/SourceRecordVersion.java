package io.github.khaledshawki.eoc.operations.domain.model;

import java.util.Objects;

/** Opaque source version used for equality and idempotency, not lexical ordering. */
public record SourceRecordVersion(String value) {

  public static final int MAX_LENGTH = 512;

  public SourceRecordVersion {
    Objects.requireNonNull(value, "Source record version cannot be null");
    value = value.strip();
    if (value.isEmpty()) {
      throw new IllegalArgumentException("Source record version cannot be blank");
    }
    if (value.length() > MAX_LENGTH) {
      throw new IllegalArgumentException(
          "Source record version cannot exceed " + MAX_LENGTH + " characters");
    }
  }
}
