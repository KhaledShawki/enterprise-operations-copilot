package io.github.khaledshawki.eoc.platform.operations.adapter.in.web;

import io.github.khaledshawki.eoc.operations.application.model.settlement.ReceivableAllocationResult;
import java.util.Objects;
import java.util.UUID;

public record ReceivableSettlementMutationResponse(
    UUID settlementId, UUID paymentId, ReceivableAllocationResponse allocation) {

  public ReceivableSettlementMutationResponse {
    Objects.requireNonNull(settlementId, "Receivable settlement response id cannot be null");
    Objects.requireNonNull(paymentId, "Receivable settlement response payment id cannot be null");
    Objects.requireNonNull(allocation, "Receivable settlement allocation response cannot be null");
  }

  static ReceivableSettlementMutationResponse from(ReceivableAllocationResult result) {
    Objects.requireNonNull(result, "Receivable allocation result cannot be null");
    return new ReceivableSettlementMutationResponse(
        result.settlementId().value(),
        result.paymentId().value(),
        ReceivableAllocationResponse.from(result));
  }
}
