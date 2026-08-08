package io.github.khaledshawki.eoc.operations.application.model.settlement;

import io.github.khaledshawki.eoc.operations.domain.model.InvoiceId;
import io.github.khaledshawki.eoc.operations.domain.model.Money;
import io.github.khaledshawki.eoc.operations.domain.model.PaymentId;
import io.github.khaledshawki.eoc.operations.domain.model.ReceivableAllocation;
import io.github.khaledshawki.eoc.operations.domain.model.ReceivableAllocationId;
import io.github.khaledshawki.eoc.operations.domain.model.ReceivableAllocationState;
import io.github.khaledshawki.eoc.operations.domain.model.ReceivableSettlement;
import io.github.khaledshawki.eoc.operations.domain.model.ReceivableSettlementId;
import java.util.Objects;

public record ReceivableAllocationResult(
    ReceivableSettlementId settlementId,
    PaymentId paymentId,
    ReceivableAllocationId allocationId,
    InvoiceId invoiceId,
    Money amount,
    ReceivableAllocationState state) {

  public ReceivableAllocationResult {
    Objects.requireNonNull(settlementId, "Receivable settlement result id cannot be null");
    Objects.requireNonNull(paymentId, "Receivable settlement result payment id cannot be null");
    Objects.requireNonNull(allocationId, "Receivable allocation result id cannot be null");
    Objects.requireNonNull(invoiceId, "Receivable allocation result invoice id cannot be null");
    Objects.requireNonNull(amount, "Receivable allocation result amount cannot be null");
    Objects.requireNonNull(state, "Receivable allocation result state cannot be null");
    if (!amount.isPositive()) {
      throw new IllegalArgumentException("Receivable allocation result amount must be positive");
    }
  }

  public static ReceivableAllocationResult from(
      ReceivableSettlement settlement, ReceivableAllocation allocation) {
    Objects.requireNonNull(settlement, "Receivable settlement cannot be null");
    Objects.requireNonNull(allocation, "Receivable allocation cannot be null");
    if (!settlement.allocations().contains(allocation)) {
      throw new IllegalArgumentException("Receivable allocation does not belong to settlement");
    }
    return new ReceivableAllocationResult(
        settlement.id(),
        settlement.paymentId(),
        allocation.id(),
        allocation.invoiceId(),
        allocation.amount(),
        allocation.state());
  }
}
