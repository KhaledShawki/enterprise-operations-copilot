package io.github.khaledshawki.eoc.platform.operations.adapter.out.persistence;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

record BusinessPartnerImportReceiptJpaId(
    UUID tenantId, UUID sourceSystemId, UUID importBatchId, UUID pageAcceptanceId)
    implements Serializable {

  BusinessPartnerImportReceiptJpaId {
    Objects.requireNonNull(tenantId, "Import receipt tenant id cannot be null");
    Objects.requireNonNull(sourceSystemId, "Import receipt source system id cannot be null");
    Objects.requireNonNull(importBatchId, "Import receipt batch id cannot be null");
    Objects.requireNonNull(pageAcceptanceId, "Import receipt acceptance id cannot be null");
  }
}
