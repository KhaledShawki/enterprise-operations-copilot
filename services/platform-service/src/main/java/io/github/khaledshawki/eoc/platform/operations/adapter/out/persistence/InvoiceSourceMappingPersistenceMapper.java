package io.github.khaledshawki.eoc.platform.operations.adapter.out.persistence;

import io.github.khaledshawki.eoc.operations.domain.model.InvoiceId;
import io.github.khaledshawki.eoc.operations.domain.model.InvoiceSourceMapping;
import io.github.khaledshawki.eoc.operations.domain.model.OperationsTenantId;
import io.github.khaledshawki.eoc.operations.domain.model.SourceRecordFingerprint;
import io.github.khaledshawki.eoc.operations.domain.model.SourceRecordIdentity;
import io.github.khaledshawki.eoc.operations.domain.model.SourceRecordVersion;
import io.github.khaledshawki.eoc.operations.domain.model.SourceSystemId;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

final class InvoiceSourceMappingPersistenceMapper {

  InvoiceSourceMappingJpaEntity toEntity(InvoiceSourceMapping sourceMapping, Instant now) {
    Objects.requireNonNull(sourceMapping, "Invoice source mapping cannot be null");
    Objects.requireNonNull(now, "Timestamp cannot be null");
    return new InvoiceSourceMappingJpaEntity(
        sourceMapping.tenantId().value(),
        sourceMapping.sourceSystemId().value(),
        sourceMapping.sourceIdentity().kind(),
        sourceMapping.sourceIdentity().value(),
        sourceMapping.invoiceId().value(),
        sourceMapping.sourceVersion().value(),
        sourceMapping.sourceModifiedAt().orElse(null),
        sourceMapping.payloadFingerprint().value(),
        now,
        now);
  }

  InvoiceSourceMapping toDomain(InvoiceSourceMappingJpaEntity entity) {
    Objects.requireNonNull(entity, "Invoice source mapping entity cannot be null");
    return InvoiceSourceMapping.reconstitute(
        OperationsTenantId.of(entity.getTenantId()),
        SourceSystemId.of(entity.getSourceSystemId()),
        new SourceRecordIdentity(entity.getSourceIdentityKind(), entity.getSourceIdentityValue()),
        InvoiceId.of(entity.getInvoiceId()),
        new SourceRecordVersion(entity.getSourceVersion()),
        Optional.ofNullable(entity.getSourceModifiedAt()),
        SourceRecordFingerprint.of(entity.getPayloadFingerprint()));
  }

  InvoiceSourceMappingJpaEntity updateEntity(
      InvoiceSourceMapping sourceMapping, InvoiceSourceMappingJpaEntity entity, Instant now) {
    Objects.requireNonNull(sourceMapping, "Invoice source mapping cannot be null");
    Objects.requireNonNull(entity, "Invoice source mapping entity cannot be null");
    Objects.requireNonNull(now, "Timestamp cannot be null");
    ensureImmutableStateMatches(sourceMapping, entity);
    entity.updateEvidence(
        sourceMapping.sourceVersion().value(),
        sourceMapping.sourceModifiedAt().orElse(null),
        sourceMapping.payloadFingerprint().value(),
        now);
    return entity;
  }

  private static void ensureImmutableStateMatches(
      InvoiceSourceMapping sourceMapping, InvoiceSourceMappingJpaEntity entity) {
    if (!sourceMapping.tenantId().value().equals(entity.getTenantId())
        || !sourceMapping.sourceSystemId().value().equals(entity.getSourceSystemId())
        || sourceMapping.sourceIdentity().kind() != entity.getSourceIdentityKind()
        || !sourceMapping.sourceIdentity().value().equals(entity.getSourceIdentityValue())
        || !sourceMapping.invoiceId().value().equals(entity.getInvoiceId())) {
      throw new IllegalArgumentException("Invoice source mapping identity mismatch");
    }
  }
}
