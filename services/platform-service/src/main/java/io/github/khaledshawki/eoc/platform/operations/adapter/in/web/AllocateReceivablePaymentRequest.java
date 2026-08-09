package io.github.khaledshawki.eoc.platform.operations.adapter.in.web;

import io.github.khaledshawki.eoc.operations.application.model.authorization.OperationsActor;
import io.github.khaledshawki.eoc.operations.application.port.in.AllocateReceivablePaymentCommand;
import io.github.khaledshawki.eoc.operations.domain.model.Money;
import java.util.Objects;
import java.util.UUID;

public record AllocateReceivablePaymentRequest(
    UUID allocationId, UUID invoiceId, MoneyRequest amount) {

  public AllocateReceivablePaymentRequest {
    Objects.requireNonNull(allocationId, "Receivable allocation request id cannot be null");
    Objects.requireNonNull(invoiceId, "Receivable allocation request invoice id cannot be null");
    Objects.requireNonNull(amount, "Receivable allocation request amount cannot be null");
  }

  AllocateReceivablePaymentCommand toCommand(OperationsActor actor, UUID tenantId, UUID paymentId) {
    Money requestedAmount = amount.toMoney();
    if (!requestedAmount.isPositive()) {
      throw new IllegalArgumentException("Receivable allocation amount must be positive");
    }
    return new AllocateReceivablePaymentCommand(
        actor, tenantId, paymentId, invoiceId, allocationId, requestedAmount);
  }
}
