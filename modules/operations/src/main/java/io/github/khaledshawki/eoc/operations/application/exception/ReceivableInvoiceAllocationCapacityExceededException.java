package io.github.khaledshawki.eoc.operations.application.exception;

import io.github.khaledshawki.eoc.operations.domain.model.InvoiceId;
import io.github.khaledshawki.eoc.operations.domain.model.Money;
import java.util.Objects;

public final class ReceivableInvoiceAllocationCapacityExceededException extends RuntimeException {

  public ReceivableInvoiceAllocationCapacityExceededException(
      InvoiceId invoiceId, Money requestedAmount, Money availableAmount) {
    super(
        "Receivable allocation for invoice "
            + Objects.requireNonNull(invoiceId, "Invoice id cannot be null").value()
            + " exceeds local allocation capacity: requested "
            + Objects.requireNonNull(requestedAmount, "Requested amount cannot be null").amount()
            + " "
            + requestedAmount.currency().value()
            + ", available "
            + Objects.requireNonNull(availableAmount, "Available amount cannot be null").amount()
            + " "
            + availableAmount.currency().value());
  }
}
