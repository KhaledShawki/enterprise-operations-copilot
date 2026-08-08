package io.github.khaledshawki.eoc.platform.operations.adapter.out.persistence;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

record PaymentImportReceiptJpaId(
    UUID tenantId, UUID sourceSystemId, UUID importBatchId, UUID pageAcceptanceId)
    implements Serializable {

  PaymentImportReceiptJpaId {
    Objects.requireNonNull(tenantId, "Payment receipt tenant id cannot be null");
    Objects.requireNonNull(sourceSystemId, "Payment receipt source system id cannot be null");
    Objects.requireNonNull(importBatchId, "Payment receipt batch id cannot be null");
    Objects.requireNonNull(pageAcceptanceId, "Payment receipt acceptance id cannot be null");
  }
}
