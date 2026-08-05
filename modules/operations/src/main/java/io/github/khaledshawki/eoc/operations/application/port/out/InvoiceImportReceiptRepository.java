package io.github.khaledshawki.eoc.operations.application.port.out;

import io.github.khaledshawki.eoc.operations.application.model.importing.InvoiceImportReceipt;
import io.github.khaledshawki.eoc.operations.domain.model.OperationsTenantId;
import io.github.khaledshawki.eoc.operations.domain.model.SourceSystemId;
import java.util.Optional;
import java.util.UUID;

public interface InvoiceImportReceiptRepository {

  Optional<InvoiceImportReceipt> find(
      OperationsTenantId tenantId,
      SourceSystemId sourceSystemId,
      UUID importBatchId,
      UUID pageAcceptanceId);

  InvoiceImportReceipt save(
      OperationsTenantId tenantId,
      SourceSystemId sourceSystemId,
      UUID importBatchId,
      InvoiceImportReceipt receipt);
}
