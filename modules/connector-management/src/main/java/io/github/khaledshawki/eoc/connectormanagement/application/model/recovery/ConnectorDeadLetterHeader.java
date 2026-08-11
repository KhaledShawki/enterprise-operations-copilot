package io.github.khaledshawki.eoc.connectormanagement.application.model.recovery;

import java.util.Base64;
import java.util.Objects;

public record ConnectorDeadLetterHeader(String name, String valueBase64) {

  public ConnectorDeadLetterHeader {
    Objects.requireNonNull(name, "Dead-letter header name cannot be null");
    Objects.requireNonNull(valueBase64, "Dead-letter header value cannot be null");
    if (name.isBlank() || name.length() > 255) {
      throw new IllegalArgumentException("Dead-letter header name is invalid");
    }
    try {
      Base64.getDecoder().decode(valueBase64);
    } catch (IllegalArgumentException exception) {
      throw new IllegalArgumentException(
          "Dead-letter header value must be valid Base64", exception);
    }
  }

  public static ConnectorDeadLetterHeader fromBytes(String name, byte[] value) {
    Objects.requireNonNull(value, "Dead-letter header bytes cannot be null");
    return new ConnectorDeadLetterHeader(name, Base64.getEncoder().encodeToString(value));
  }

  public byte[] valueBytes() {
    return Base64.getDecoder().decode(valueBase64);
  }
}
