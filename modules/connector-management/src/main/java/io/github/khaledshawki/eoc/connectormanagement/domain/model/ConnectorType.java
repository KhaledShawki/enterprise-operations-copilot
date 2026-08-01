package io.github.khaledshawki.eoc.connectormanagement.domain.model;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

public record ConnectorType(String value) {

  public static final int MAX_LENGTH = 63;

  private static final Pattern VALID_FORMAT = Pattern.compile("^[a-z0-9]+(?:-[a-z0-9]+)*$");

  public ConnectorType {
    Objects.requireNonNull(value, "Connector type cannot be null");
    value = value.trim().toLowerCase(Locale.ROOT);

    if (value.isEmpty()) {
      throw new IllegalArgumentException("Connector type cannot be empty");
    }

    if (value.length() > MAX_LENGTH) {
      throw new IllegalArgumentException(
          "Connector type cannot be longer than " + MAX_LENGTH + " characters");
    }

    if (!VALID_FORMAT.matcher(value).matches()) {
      throw new IllegalArgumentException("Connector type has an invalid format");
    }
  }

  public static ConnectorType of(String value) {
    return new ConnectorType(value);
  }
}
