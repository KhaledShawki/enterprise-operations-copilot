package io.github.khaledshawki.eoc.operations.application.port.out;

import io.github.khaledshawki.eoc.operations.application.model.importing.PaymentImportReceipt;
import io.github.khaledshawki.eoc.operations.domain.model.OperationsTenantId;
import io.github.khaledshawki.eoc.operations.domain.model.SourceSystemId;
import java.util.Optional;
import java.util.UUID;

public interface PaymentImportReceiptRepository {

  Optional<PaymentImportReceipt> find(
      OperationsTenantId tenantId,
      SourceSystemId sourceSystemId,
      UUID importBatchId,
      UUID pageAcceptanceId);

  PaymentImportReceipt save(
      OperationsTenantId tenantId,
      SourceSystemId sourceSystemId,
      UUID importBatchId,
      PaymentImportReceipt receipt);
}
