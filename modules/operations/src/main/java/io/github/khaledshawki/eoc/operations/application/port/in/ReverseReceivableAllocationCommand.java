package io.github.khaledshawki.eoc.operations.application.port.in;

import io.github.khaledshawki.eoc.operations.application.model.authorization.OperationsActor;
import java.util.Objects;
import java.util.UUID;

/** Reverses one historical allocation; repeating the same reversal is idempotent. */
public record ReverseReceivableAllocationCommand(
    OperationsActor actor, UUID tenantId, UUID paymentId, UUID invoiceId, UUID allocationId) {

  public ReverseReceivableAllocationCommand {
    Objects.requireNonNull(actor, "Operations actor cannot be null");
    Objects.requireNonNull(tenantId, "Receivable settlement tenant id cannot be null");
    Objects.requireNonNull(paymentId, "Receivable settlement payment id cannot be null");
    Objects.requireNonNull(invoiceId, "Receivable settlement invoice id cannot be null");
    Objects.requireNonNull(allocationId, "Receivable allocation id cannot be null");
  }
}
