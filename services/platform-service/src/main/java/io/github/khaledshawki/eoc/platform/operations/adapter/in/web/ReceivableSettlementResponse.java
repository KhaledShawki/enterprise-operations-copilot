package io.github.khaledshawki.eoc.platform.operations.adapter.in.web;

import io.github.khaledshawki.eoc.operations.application.port.in.ReceivableSettlementResult;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record ReceivableSettlementResponse(
    PaymentResponse payment,
    UUID settlementId,
    MoneyResponse allocatedAmount,
    MoneyResponse unappliedAmount,
    List<ReceivableAllocationResponse> allocations) {

  public ReceivableSettlementResponse {
    Objects.requireNonNull(payment, "Receivable settlement payment response cannot be null");
    Objects.requireNonNull(allocatedAmount, "Receivable allocated amount response cannot be null");
    Objects.requireNonNull(unappliedAmount, "Receivable unapplied amount response cannot be null");
    Objects.requireNonNull(
        allocations, "Receivable settlement allocation responses cannot be null");
    if (allocations.stream().anyMatch(Objects::isNull)) {
      throw new NullPointerException(
          "Receivable settlement allocation responses cannot contain null");
    }
    allocations = List.copyOf(allocations);
  }

  static ReceivableSettlementResponse from(ReceivableSettlementResult result) {
    Objects.requireNonNull(result, "Receivable settlement result cannot be null");
    return new ReceivableSettlementResponse(
        PaymentResponse.from(result.payment()),
        result.settlementId().map(id -> id.value()).orElse(null),
        MoneyResponse.from(result.allocatedAmount()),
        MoneyResponse.from(result.unappliedAmount()),
        result.allocations().stream().map(ReceivableAllocationResponse::from).toList());
  }
}
