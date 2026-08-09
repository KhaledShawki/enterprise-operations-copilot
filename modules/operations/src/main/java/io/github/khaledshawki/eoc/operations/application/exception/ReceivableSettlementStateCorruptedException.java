package io.github.khaledshawki.eoc.operations.application.exception;

import java.util.Objects;

public final class ReceivableSettlementStateCorruptedException extends RuntimeException {

  public ReceivableSettlementStateCorruptedException(String detail) {
    super(requireDetail(detail));
  }

  public ReceivableSettlementStateCorruptedException(String detail, Throwable cause) {
    super(
        requireDetail(detail),
        Objects.requireNonNull(cause, "Receivable settlement corruption cause cannot be null"));
  }

  private static String requireDetail(String detail) {
    Objects.requireNonNull(detail, "Receivable settlement corruption detail cannot be null");
    if (detail.isBlank()) {
      throw new IllegalArgumentException("Receivable settlement corruption detail cannot be blank");
    }
    return detail;
  }
}
