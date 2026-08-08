package io.github.khaledshawki.eoc.platform.operations.adapter.out.persistence;

import io.github.khaledshawki.eoc.operations.domain.model.OperationsTenantId;
import io.github.khaledshawki.eoc.operations.domain.model.PaymentId;
import io.github.khaledshawki.eoc.operations.domain.model.PaymentSourceMapping;
import io.github.khaledshawki.eoc.operations.domain.model.SourceRecordFingerprint;
import io.github.khaledshawki.eoc.operations.domain.model.SourceRecordIdentity;
import io.github.khaledshawki.eoc.operations.domain.model.SourceRecordVersion;
import io.github.khaledshawki.eoc.operations.domain.model.SourceSystemId;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

final class PaymentSourceMappingPersistenceMapper {

  PaymentSourceMappingJpaEntity toEntity(PaymentSourceMapping sourceMapping, Instant now) {
    Objects.requireNonNull(sourceMapping, "Payment source mapping cannot be null");
    Objects.requireNonNull(now, "Timestamp cannot be null");
    return new PaymentSourceMappingJpaEntity(
        sourceMapping.tenantId().value(),
        sourceMapping.sourceSystemId().value(),
        sourceMapping.sourceIdentity().kind(),
        sourceMapping.sourceIdentity().value(),
        sourceMapping.paymentId().value(),
        sourceMapping.sourceVersion().value(),
        sourceMapping.sourceModifiedAt().orElse(null),
        sourceMapping.payloadFingerprint().value(),
        now,
        now);
  }

  PaymentSourceMapping toDomain(PaymentSourceMappingJpaEntity entity) {
    Objects.requireNonNull(entity, "Payment source mapping entity cannot be null");
    return PaymentSourceMapping.reconstitute(
        OperationsTenantId.of(entity.getTenantId()),
        SourceSystemId.of(entity.getSourceSystemId()),
        new SourceRecordIdentity(entity.getSourceIdentityKind(), entity.getSourceIdentityValue()),
        PaymentId.of(entity.getPaymentId()),
        new SourceRecordVersion(entity.getSourceVersion()),
        Optional.ofNullable(entity.getSourceModifiedAt()),
        SourceRecordFingerprint.of(entity.getPayloadFingerprint()));
  }

  PaymentSourceMappingJpaEntity updateEntity(
      PaymentSourceMapping sourceMapping, PaymentSourceMappingJpaEntity entity, Instant now) {
    Objects.requireNonNull(sourceMapping, "Payment source mapping cannot be null");
    Objects.requireNonNull(entity, "Payment source mapping entity cannot be null");
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
      PaymentSourceMapping sourceMapping, PaymentSourceMappingJpaEntity entity) {
    if (!sourceMapping.tenantId().value().equals(entity.getTenantId())
        || !sourceMapping.sourceSystemId().value().equals(entity.getSourceSystemId())
        || sourceMapping.sourceIdentity().kind() != entity.getSourceIdentityKind()
        || !sourceMapping.sourceIdentity().value().equals(entity.getSourceIdentityValue())
        || !sourceMapping.paymentId().value().equals(entity.getPaymentId())) {
      throw new IllegalArgumentException("Payment source mapping identity mismatch");
    }
  }
}
