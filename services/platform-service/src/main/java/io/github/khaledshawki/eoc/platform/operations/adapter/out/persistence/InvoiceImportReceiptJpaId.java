package io.github.khaledshawki.eoc.platform.operations.adapter.out.persistence;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

record InvoiceImportReceiptJpaId(
    UUID tenantId, UUID sourceSystemId, UUID importBatchId, UUID pageAcceptanceId)
    implements Serializable {

  InvoiceImportReceiptJpaId {
    Objects.requireNonNull(tenantId, "Invoice receipt tenant id cannot be null");
    Objects.requireNonNull(sourceSystemId, "Invoice receipt source system id cannot be null");
    Objects.requireNonNull(importBatchId, "Invoice receipt batch id cannot be null");
    Objects.requireNonNull(pageAcceptanceId, "Invoice receipt acceptance id cannot be null");
  }
}
