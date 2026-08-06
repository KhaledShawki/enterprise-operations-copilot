package io.github.khaledshawki.eoc.operations.application.exception;

public final class ConcurrentInvoiceImportException extends RuntimeException {

  public ConcurrentInvoiceImportException(String message, Throwable cause) {
    super(message, cause);
  }
}
