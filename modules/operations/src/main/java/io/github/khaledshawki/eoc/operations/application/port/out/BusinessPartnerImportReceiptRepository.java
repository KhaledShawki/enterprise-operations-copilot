package io.github.khaledshawki.eoc.operations.application.port.out;

import io.github.khaledshawki.eoc.operations.application.model.importing.BusinessPartnerImportReceipt;
import io.github.khaledshawki.eoc.operations.domain.model.OperationsTenantId;
import io.github.khaledshawki.eoc.operations.domain.model.SourceSystemId;
import java.util.Optional;
import java.util.UUID;

public interface BusinessPartnerImportReceiptRepository {

  Optional<BusinessPartnerImportReceipt> find(
      OperationsTenantId tenantId,
      SourceSystemId sourceSystemId,
      UUID importBatchId,
      UUID pageAcceptanceId);

  BusinessPartnerImportReceipt save(
      OperationsTenantId tenantId,
      SourceSystemId sourceSystemId,
      UUID importBatchId,
      BusinessPartnerImportReceipt receipt);
}
