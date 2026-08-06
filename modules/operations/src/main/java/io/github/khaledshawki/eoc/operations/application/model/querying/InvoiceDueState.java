package io.github.khaledshawki.eoc.operations.application.model.querying;

import io.github.khaledshawki.eoc.operations.domain.model.Invoice;
import java.time.LocalDate;
import java.util.Objects;

public enum InvoiceDueState {
  OVERDUE,
  DUE_TODAY,
  NOT_DUE,
  SETTLED;

  public static InvoiceDueState from(Invoice invoice, LocalDate businessDate) {
    Objects.requireNonNull(invoice, "Invoice cannot be null");
    Objects.requireNonNull(businessDate, "Business date cannot be null");
    if (invoice.cancelled() || invoice.openAmount().isZero()) {
      return SETTLED;
    }
    if (invoice.dueDate().isBefore(businessDate)) {
      return OVERDUE;
    }
    if (invoice.dueDate().isEqual(businessDate)) {
      return DUE_TODAY;
    }
    return NOT_DUE;
  }
}
