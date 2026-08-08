package io.github.khaledshawki.eoc.platform.operations.adapter.out.persistence;

import io.github.khaledshawki.eoc.operations.domain.model.SourceRecordIdentity;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

record PaymentSourceMappingJpaId(
    UUID tenantId,
    UUID sourceSystemId,
    SourceRecordIdentity.Kind sourceIdentityKind,
    String sourceIdentityValue)
    implements Serializable {

  PaymentSourceMappingJpaId {
    Objects.requireNonNull(tenantId, "Payment source mapping tenant id cannot be null");
    Objects.requireNonNull(sourceSystemId, "Payment source mapping system id cannot be null");
    Objects.requireNonNull(
        sourceIdentityKind, "Payment source mapping identity kind cannot be null");
    Objects.requireNonNull(
        sourceIdentityValue, "Payment source mapping identity value cannot be null");
  }
}
