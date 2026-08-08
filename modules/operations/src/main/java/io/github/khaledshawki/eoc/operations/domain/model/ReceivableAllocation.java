package io.github.khaledshawki.eoc.operations.domain.model;

import java.util.Objects;

public record ReceivableAllocation(
    ReceivableAllocationId id, InvoiceId invoiceId, Money amount, ReceivableAllocationState state) {

  public ReceivableAllocation {
    Objects.requireNonNull(id, "Receivable allocation id cannot be null");
    Objects.requireNonNull(invoiceId, "Receivable allocation invoice id cannot be null");
    Objects.requireNonNull(amount, "Receivable allocation amount cannot be null");
    Objects.requireNonNull(state, "Receivable allocation state cannot be null");
    if (!amount.isPositive()) {
      throw new IllegalArgumentException("Receivable allocation amount must be positive");
    }
  }

  public static ReceivableAllocation active(
      ReceivableAllocationId id, InvoiceId invoiceId, Money amount) {
    return new ReceivableAllocation(id, invoiceId, amount, ReceivableAllocationState.ACTIVE);
  }

  public boolean active() {
    return state == ReceivableAllocationState.ACTIVE;
  }

  public ReceivableAllocation reverse() {
    if (!active()) {
      return this;
    }
    return new ReceivableAllocation(id, invoiceId, amount, ReceivableAllocationState.REVERSED);
  }
}
