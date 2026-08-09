package io.github.khaledshawki.eoc.operations.application.port.out;

import io.github.khaledshawki.eoc.operations.application.model.reconciliation.ReceivableReconciliationEvidence;
import io.github.khaledshawki.eoc.operations.domain.model.BusinessPartnerId;
import io.github.khaledshawki.eoc.operations.domain.model.CurrencyCode;
import io.github.khaledshawki.eoc.operations.domain.model.InvoiceId;
import io.github.khaledshawki.eoc.operations.domain.model.OperationsTenantId;

public interface ReceivableReconciliationEvidenceRepository {

  /**
   * Loads EOC-local ACTIVE allocation evidence for one Invoice.
   *
   * <p>The expected customer and currency are the current canonical Invoice facts. The adapter must
   * report structural divergence rather than coercing incompatible settlement or Payment state.
   */
  ReceivableReconciliationEvidence load(
      OperationsTenantId tenantId,
      InvoiceId invoiceId,
      BusinessPartnerId expectedCustomerId,
      CurrencyCode expectedCurrency);
}
