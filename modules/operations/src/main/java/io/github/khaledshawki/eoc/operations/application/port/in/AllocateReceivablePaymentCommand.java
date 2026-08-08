package io.github.khaledshawki.eoc.operations.application.port.in;

import io.github.khaledshawki.eoc.operations.application.model.authorization.OperationsActor;
import io.github.khaledshawki.eoc.operations.domain.model.Money;
import java.util.Objects;
import java.util.UUID;

/**
 * Requests one local cash allocation. The caller-supplied allocation id is also the idempotency
 * identity: an exact ACTIVE replay is a no-op, while reuse with different facts or after reversal
 * is rejected.
 */
public record AllocateReceivablePaymentCommand(
    OperationsActor actor,
    UUID tenantId,
    UUID paymentId,
    UUID invoiceId,
    UUID allocationId,
    Money amount) {

  public AllocateReceivablePaymentCommand {
    Objects.requireNonNull(actor, "Operations actor cannot be null");
    Objects.requireNonNull(tenantId, "Receivable settlement tenant id cannot be null");
    Objects.requireNonNull(paymentId, "Receivable settlement payment id cannot be null");
    Objects.requireNonNull(invoiceId, "Receivable settlement invoice id cannot be null");
    Objects.requireNonNull(allocationId, "Receivable allocation id cannot be null");
    Objects.requireNonNull(amount, "Receivable allocation amount cannot be null");
  }
}
