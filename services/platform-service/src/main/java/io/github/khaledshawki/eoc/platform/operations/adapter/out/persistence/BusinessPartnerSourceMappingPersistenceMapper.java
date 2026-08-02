package io.github.khaledshawki.eoc.platform.operations.adapter.out.persistence;

import io.github.khaledshawki.eoc.operations.domain.model.BusinessPartnerId;
import io.github.khaledshawki.eoc.operations.domain.model.BusinessPartnerSourceMapping;
import io.github.khaledshawki.eoc.operations.domain.model.OperationsTenantId;
import io.github.khaledshawki.eoc.operations.domain.model.SourceRecordIdentity;
import io.github.khaledshawki.eoc.operations.domain.model.SourceRecordVersion;
import io.github.khaledshawki.eoc.operations.domain.model.SourceSystemId;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

final class BusinessPartnerSourceMappingPersistenceMapper {

  BusinessPartnerSourceMappingJpaEntity toEntity(
      BusinessPartnerSourceMapping sourceMapping, Instant now) {
    Objects.requireNonNull(sourceMapping, "Business partner source mapping cannot be null");
    Objects.requireNonNull(now, "Timestamp cannot be null");
    return new BusinessPartnerSourceMappingJpaEntity(
        sourceMapping.tenantId().value(),
        sourceMapping.sourceSystemId().value(),
        sourceMapping.sourceIdentity().kind(),
        sourceMapping.sourceIdentity().value(),
        sourceMapping.businessPartnerId().value(),
        sourceMapping.sourceVersion().value(),
        sourceMapping.sourceModifiedAt().orElse(null),
        now,
        now);
  }

  BusinessPartnerSourceMapping toDomain(BusinessPartnerSourceMappingJpaEntity entity) {
    Objects.requireNonNull(entity, "Business partner source mapping entity cannot be null");
    return BusinessPartnerSourceMapping.reconstitute(
        OperationsTenantId.of(entity.getTenantId()),
        SourceSystemId.of(entity.getSourceSystemId()),
        new SourceRecordIdentity(entity.getSourceIdentityKind(), entity.getSourceIdentityValue()),
        BusinessPartnerId.of(entity.getBusinessPartnerId()),
        new SourceRecordVersion(entity.getSourceVersion()),
        Optional.ofNullable(entity.getSourceModifiedAt()));
  }

  BusinessPartnerSourceMappingJpaEntity updateEntity(
      BusinessPartnerSourceMapping sourceMapping,
      BusinessPartnerSourceMappingJpaEntity entity,
      Instant now) {
    Objects.requireNonNull(sourceMapping, "Business partner source mapping cannot be null");
    Objects.requireNonNull(entity, "Business partner source mapping entity cannot be null");
    Objects.requireNonNull(now, "Timestamp cannot be null");
    ensureImmutableStateMatches(sourceMapping, entity);
    entity.updateVersion(
        sourceMapping.sourceVersion().value(), sourceMapping.sourceModifiedAt().orElse(null), now);
    return entity;
  }

  private static void ensureImmutableStateMatches(
      BusinessPartnerSourceMapping sourceMapping, BusinessPartnerSourceMappingJpaEntity entity) {
    if (!sourceMapping.tenantId().value().equals(entity.getTenantId())
        || !sourceMapping.sourceSystemId().value().equals(entity.getSourceSystemId())
        || sourceMapping.sourceIdentity().kind() != entity.getSourceIdentityKind()
        || !sourceMapping.sourceIdentity().value().equals(entity.getSourceIdentityValue())
        || !sourceMapping.businessPartnerId().value().equals(entity.getBusinessPartnerId())) {
      throw new IllegalArgumentException("Business partner source mapping identity mismatch");
    }
  }
}
