package io.github.khaledshawki.eoc.operations.application.exception;

public final class ConcurrentPaymentImportException extends RuntimeException {

  public ConcurrentPaymentImportException(String message, Throwable cause) {
    super(message, cause);
  }
}
