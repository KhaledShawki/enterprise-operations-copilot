package io.github.khaledshawki.eoc.operations.application.port.out;

import io.github.khaledshawki.eoc.operations.application.model.settlement.ReceivableAllocationResult;
import io.github.khaledshawki.eoc.operations.domain.model.InvoiceId;
import io.github.khaledshawki.eoc.operations.domain.model.OperationsTenantId;
import io.github.khaledshawki.eoc.operations.domain.model.PaymentId;
import io.github.khaledshawki.eoc.operations.domain.model.ReceivableAllocationId;
import java.util.function.Supplier;

/**
 * Atomic coordination boundary for one Payment/Invoice cash-application mutation.
 *
 * <p>An infrastructure implementation must invoke {@code work} only after acquiring coordination
 * that serializes concurrent mutations sharing the Payment, the Invoice, or the allocation identity
 * within the tenant, and must hold that coordination until the transaction commits or rolls back.
 * This makes one-settlement-per-Payment, allocation idempotency, and cross-Payment Invoice-capacity
 * checks authoritative rather than best-effort reads. All required coordination must be acquired in
 * a deterministic order before invoking {@code work} so overlapping multi-key mutations do not
 * introduce lock-order deadlocks.
 */
@FunctionalInterface
public interface ReceivableSettlementMutationUnitOfWork {

  ReceivableAllocationResult execute(
      OperationsTenantId tenantId,
      PaymentId paymentId,
      InvoiceId invoiceId,
      ReceivableAllocationId allocationId,
      Supplier<ReceivableAllocationResult> work);
}
