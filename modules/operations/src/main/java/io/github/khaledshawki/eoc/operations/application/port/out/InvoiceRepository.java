package io.github.khaledshawki.eoc.operations.application.port.out;

import io.github.khaledshawki.eoc.operations.domain.model.Invoice;
import io.github.khaledshawki.eoc.operations.domain.model.InvoiceId;
import io.github.khaledshawki.eoc.operations.domain.model.OperationsTenantId;
import java.util.Optional;

public interface InvoiceRepository {

  Invoice save(Invoice invoice);

  Optional<Invoice> findById(OperationsTenantId tenantId, InvoiceId invoiceId);
}
