package io.github.khaledshawki.eoc.platform.operations.adapter.in.web;

import io.github.khaledshawki.eoc.operations.application.model.settlement.ReceivableAllocationResult;
import io.github.khaledshawki.eoc.operations.domain.model.ReceivableAllocationState;
import java.util.Objects;
import java.util.UUID;

public record ReceivableAllocationResponse(
    UUID id, UUID invoiceId, MoneyResponse amount, ReceivableAllocationState state) {

  public ReceivableAllocationResponse {
    Objects.requireNonNull(id, "Receivable allocation response id cannot be null");
    Objects.requireNonNull(invoiceId, "Receivable allocation response invoice id cannot be null");
    Objects.requireNonNull(amount, "Receivable allocation response amount cannot be null");
    Objects.requireNonNull(state, "Receivable allocation response state cannot be null");
  }

  static ReceivableAllocationResponse from(ReceivableAllocationResult result) {
    Objects.requireNonNull(result, "Receivable allocation result cannot be null");
    return new ReceivableAllocationResponse(
        result.allocationId().value(),
        result.invoiceId().value(),
        MoneyResponse.from(result.amount()),
        result.state());
  }
}
