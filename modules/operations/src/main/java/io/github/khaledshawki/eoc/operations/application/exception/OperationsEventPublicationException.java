package io.github.khaledshawki.eoc.operations.application.exception;

import io.github.khaledshawki.eoc.operations.application.model.outbox.OperationsOutboxPublicationRetry;

public final class OperationsEventPublicationException extends RuntimeException {

  private final String failureCode;
  private final boolean retryable;

  public OperationsEventPublicationException(
      String failureCode, boolean retryable, Throwable cause) {
    super("Operations integration event publication failed", cause);
    this.failureCode = OperationsOutboxPublicationRetry.requireFailureCode(failureCode);
    this.retryable = retryable;
  }

  public String failureCode() {
    return failureCode;
  }

  public boolean retryable() {
    return retryable;
  }
}
