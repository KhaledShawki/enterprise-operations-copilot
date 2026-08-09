package io.github.khaledshawki.eoc.platform.operations.adapter.in.web;

import io.github.khaledshawki.eoc.operations.application.model.authorization.OperationsActor;
import io.github.khaledshawki.eoc.operations.application.port.in.ReverseReceivableAllocationCommand;
import java.util.Objects;
import java.util.UUID;

public record ReverseReceivableAllocationRequest(UUID invoiceId) {

  public ReverseReceivableAllocationRequest {
    Objects.requireNonNull(invoiceId, "Receivable allocation reversal invoice id cannot be null");
  }

  ReverseReceivableAllocationCommand toCommand(
      OperationsActor actor, UUID tenantId, UUID paymentId, UUID allocationId) {
    return new ReverseReceivableAllocationCommand(
        actor, tenantId, paymentId, invoiceId, allocationId);
  }
}
