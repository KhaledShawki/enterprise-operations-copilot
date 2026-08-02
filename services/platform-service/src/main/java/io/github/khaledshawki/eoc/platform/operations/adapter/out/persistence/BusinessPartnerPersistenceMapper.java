package io.github.khaledshawki.eoc.platform.operations.adapter.out.persistence;

import io.github.khaledshawki.eoc.operations.domain.model.BusinessPartner;
import io.github.khaledshawki.eoc.operations.domain.model.BusinessPartnerId;
import io.github.khaledshawki.eoc.operations.domain.model.BusinessPartnerProfile;
import io.github.khaledshawki.eoc.operations.domain.model.OperationsTenantId;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

final class BusinessPartnerPersistenceMapper {

  BusinessPartnerJpaEntity toEntity(BusinessPartner businessPartner, Instant now) {
    Objects.requireNonNull(businessPartner, "Business partner cannot be null");
    Objects.requireNonNull(now, "Timestamp cannot be null");
    BusinessPartnerProfile profile = businessPartner.profile();
    return new BusinessPartnerJpaEntity(
        businessPartner.id().value(),
        businessPartner.tenantId().value(),
        profile.partnerNumber(),
        profile.displayName(),
        profile.emailAddress().orElse(null),
        businessPartner.roles(),
        now,
        now);
  }

  BusinessPartner toDomain(BusinessPartnerJpaEntity entity) {
    Objects.requireNonNull(entity, "Business partner entity cannot be null");
    return BusinessPartner.reconstitute(
        BusinessPartnerId.of(entity.getId()),
        OperationsTenantId.of(entity.getTenantId()),
        new BusinessPartnerProfile(
            entity.getPartnerNumber(),
            entity.getDisplayName(),
            Optional.ofNullable(entity.getEmailAddress())),
        entity.getRoles());
  }

  BusinessPartnerJpaEntity updateEntity(
      BusinessPartner businessPartner, BusinessPartnerJpaEntity entity, Instant now) {
    Objects.requireNonNull(businessPartner, "Business partner cannot be null");
    Objects.requireNonNull(entity, "Business partner entity cannot be null");
    Objects.requireNonNull(now, "Timestamp cannot be null");
    ensureImmutableStateMatches(businessPartner, entity);
    BusinessPartnerProfile profile = businessPartner.profile();
    entity.updateMutableState(
        profile.partnerNumber(),
        profile.displayName(),
        profile.emailAddress().orElse(null),
        businessPartner.roles(),
        now);
    return entity;
  }

  private static void ensureImmutableStateMatches(
      BusinessPartner businessPartner, BusinessPartnerJpaEntity entity) {
    if (!businessPartner.id().value().equals(entity.getId())) {
      throw new IllegalArgumentException("Business partner id mismatch");
    }
    if (!businessPartner.tenantId().value().equals(entity.getTenantId())) {
      throw new IllegalArgumentException("Business partner tenant id mismatch");
    }
  }
}
