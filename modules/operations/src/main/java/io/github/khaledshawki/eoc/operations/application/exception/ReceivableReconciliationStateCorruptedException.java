package io.github.khaledshawki.eoc.operations.application.exception;

import java.util.Objects;

public final class ReceivableReconciliationStateCorruptedException extends RuntimeException {

  public ReceivableReconciliationStateCorruptedException(String detail) {
    super(requireDetail(detail));
  }

  public ReceivableReconciliationStateCorruptedException(String detail, Throwable cause) {
    super(
        requireDetail(detail),
        Objects.requireNonNull(cause, "Receivable reconciliation corruption cause cannot be null"));
  }

  private static String requireDetail(String detail) {
    Objects.requireNonNull(detail, "Receivable reconciliation corruption detail cannot be null");
    if (detail.isBlank()) {
      throw new IllegalArgumentException(
          "Receivable reconciliation corruption detail cannot be blank");
    }
    return detail;
  }
}
