package io.github.khaledshawki.eoc.connectormanagement.application.exception;

import io.github.khaledshawki.eoc.connectormanagement.application.model.outbox.ConnectorOutboxPublicationRetry;

public final class ConnectorEventConsumptionException extends RuntimeException {

  private final String failureCode;
  private final boolean retryable;

  public ConnectorEventConsumptionException(
      String failureCode, boolean retryable, Throwable cause) {
    super("Connector integration event consumption failed", cause);
    this.failureCode = ConnectorOutboxPublicationRetry.requireFailureCode(failureCode);
    this.retryable = retryable;
  }

  public String failureCode() {
    return failureCode;
  }

  public boolean retryable() {
    return retryable;
  }
}
