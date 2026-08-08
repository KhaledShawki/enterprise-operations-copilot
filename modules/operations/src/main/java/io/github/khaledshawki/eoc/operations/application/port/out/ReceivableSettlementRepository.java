package io.github.khaledshawki.eoc.operations.application.port.out;

import io.github.khaledshawki.eoc.operations.domain.model.CurrencyCode;
import io.github.khaledshawki.eoc.operations.domain.model.InvoiceId;
import io.github.khaledshawki.eoc.operations.domain.model.Money;
import io.github.khaledshawki.eoc.operations.domain.model.OperationsTenantId;
import io.github.khaledshawki.eoc.operations.domain.model.PaymentId;
import io.github.khaledshawki.eoc.operations.domain.model.ReceivableAllocationId;
import io.github.khaledshawki.eoc.operations.domain.model.ReceivableSettlement;
import java.util.Optional;

public interface ReceivableSettlementRepository {

  ReceivableSettlement save(ReceivableSettlement settlement);

  Optional<ReceivableSettlement> findByPaymentId(OperationsTenantId tenantId, PaymentId paymentId);

  Optional<ReceivableSettlement> findByAllocationId(
      OperationsTenantId tenantId, ReceivableAllocationId allocationId);

  /**
   * Returns the sum of all ACTIVE local allocations against the Invoice across every Payment
   * settlement in the tenant. The caller supplies the expected currency so an empty result can be
   * represented as canonical zero without consulting another bounded contract.
   */
  Money activeAllocatedAmountForInvoice(
      OperationsTenantId tenantId, InvoiceId invoiceId, CurrencyCode currency);
}
