package io.github.khaledshawki.eoc.platform.operations.adapter.out.persistence;

import io.github.khaledshawki.eoc.operations.domain.model.SourceRecordIdentity;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

record BusinessPartnerSourceMappingJpaId(
    UUID tenantId,
    UUID sourceSystemId,
    SourceRecordIdentity.Kind sourceIdentityKind,
    String sourceIdentityValue)
    implements Serializable {

  BusinessPartnerSourceMappingJpaId {
    Objects.requireNonNull(tenantId, "Source mapping tenant id cannot be null");
    Objects.requireNonNull(sourceSystemId, "Source mapping system id cannot be null");
    Objects.requireNonNull(sourceIdentityKind, "Source mapping identity kind cannot be null");
    Objects.requireNonNull(sourceIdentityValue, "Source mapping identity value cannot be null");
  }
}
