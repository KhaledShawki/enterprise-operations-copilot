package io.github.khaledshawki.eoc.platform.operations.adapter.out.persistence;

import io.github.khaledshawki.eoc.operations.domain.model.SourceRecordIdentity;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

record InvoiceSourceMappingJpaId(
    UUID tenantId,
    UUID sourceSystemId,
    SourceRecordIdentity.Kind sourceIdentityKind,
    String sourceIdentityValue)
    implements Serializable {

  InvoiceSourceMappingJpaId {
    Objects.requireNonNull(tenantId, "Invoice source mapping tenant id cannot be null");
    Objects.requireNonNull(sourceSystemId, "Invoice source mapping system id cannot be null");
    Objects.requireNonNull(
        sourceIdentityKind, "Invoice source mapping identity kind cannot be null");
    Objects.requireNonNull(
        sourceIdentityValue, "Invoice source mapping identity value cannot be null");
  }
}
