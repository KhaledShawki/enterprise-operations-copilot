package io.github.khaledshawki.eoc.analytics.domain.model;

public enum InvoiceReceivableStatus {
  OPEN,
  PARTIALLY_PAID,
  PAID,
  CANCELLED;

  public static InvoiceReceivableStatus fromContractCode(String value) {
    if (value == null) {
      throw new NullPointerException("Invoice receivable status cannot be null");
    }
    try {
      return InvoiceReceivableStatus.valueOf(value);
    } catch (IllegalArgumentException exception) {
      throw new IllegalArgumentException(
          "Invoice receivable status must be a supported contract code", exception);
    }
  }
}
