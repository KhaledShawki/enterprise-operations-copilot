package io.github.khaledshawki.eoc.connectormanagement.domain.model;

import java.util.Objects;

public record ConnectorName(String value) {

  public static final int MAX_LENGTH = 100;

  public ConnectorName {
    Objects.requireNonNull(value, "Connector name cannot be null");
    value = value.trim();

    if (value.isEmpty()) {
      throw new IllegalArgumentException("Connector name cannot be empty");
    }

    if (value.length() > MAX_LENGTH) {
      throw new IllegalArgumentException(
          "Connector name cannot be longer than " + MAX_LENGTH + " characters");
    }
  }

  public static ConnectorName of(String value) {
    return new ConnectorName(value);
  }
}
