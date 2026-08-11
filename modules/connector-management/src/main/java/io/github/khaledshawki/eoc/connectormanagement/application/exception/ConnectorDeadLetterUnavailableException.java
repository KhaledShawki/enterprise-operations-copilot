package io.github.khaledshawki.eoc.connectormanagement.application.exception;

public final class ConnectorDeadLetterUnavailableException extends RuntimeException {

  public ConnectorDeadLetterUnavailableException(String message, Throwable cause) {
    super(message, cause);
  }

  public ConnectorDeadLetterUnavailableException(String message) {
    super(message);
  }
}
