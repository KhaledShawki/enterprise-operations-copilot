package io.github.khaledshawki.eoc.connectormanagement.application.exception;

import java.util.Objects;

public final class InvalidConnectorConfigurationException extends RuntimeException {

  public InvalidConnectorConfigurationException(String message, Throwable cause) {
    super(Objects.requireNonNull(message, "Message cannot be null"), cause);
  }
}
