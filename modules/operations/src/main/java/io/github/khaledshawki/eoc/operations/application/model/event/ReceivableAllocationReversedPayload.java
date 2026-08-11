package io.github.khaledshawki.eoc.operations.application.model.event;

import java.util.Objects;
import java.util.UUID;

public record ReceivableAllocationReversedPayload(
    UUID settlementId,
    UUID paymentId,
    UUID allocationId,
    UUID invoiceId,
    OperationsMoneyPayload amount)
    implements OperationsIntegrationEventPayload {

  public ReceivableAllocationReversedPayload {
    Objects.requireNonNull(settlementId, "Event settlement id cannot be null");
    Objects.requireNonNull(paymentId, "Event allocation payment id cannot be null");
    Objects.requireNonNull(allocationId, "Event allocation id cannot be null");
    Objects.requireNonNull(invoiceId, "Event allocation invoice id cannot be null");
    Objects.requireNonNull(amount, "Event allocation amount cannot be null");
    if (!amount.toMoney().isPositive()) {
      throw new IllegalArgumentException("Event allocation amount must be positive");
    }
  }

  @Override
  public UUID aggregateId() {
    return settlementId;
  }
}
