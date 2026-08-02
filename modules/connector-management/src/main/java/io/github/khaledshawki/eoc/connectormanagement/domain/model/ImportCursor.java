package io.github.khaledshawki.eoc.connectormanagement.domain.model;

import java.util.Objects;

/** Opaque durable source position. Only the source adapter that created it may interpret it. */
public record ImportCursor(String value) {

  public static final int MAX_LENGTH = 2048;

  public ImportCursor {
    Objects.requireNonNull(value, "Import cursor cannot be null");
    value = value.trim();
    if (value.isEmpty()) {
      throw new IllegalArgumentException("Import cursor cannot be empty");
    }
    if (value.length() > MAX_LENGTH) {
      throw new IllegalArgumentException(
          "Import cursor cannot be longer than " + MAX_LENGTH + " characters");
    }
  }
}
