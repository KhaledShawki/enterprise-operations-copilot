package io.github.khaledshawki.eoc.platform.tenantaccess.adapter.out.persistence;

import io.github.khaledshawki.eoc.tenantaccess.domain.model.PlatformUserId;
import io.github.khaledshawki.eoc.tenantaccess.domain.model.TenantId;
import io.github.khaledshawki.eoc.tenantaccess.domain.model.TenantMembership;
import io.github.khaledshawki.eoc.tenantaccess.domain.model.TenantMembershipId;
import java.time.Instant;
import java.util.Objects;

final class TenantMembershipPersistenceMapper {

  TenantMembershipJpaEntity toEntity(TenantMembership membership, Instant now) {
    Objects.requireNonNull(membership, "Tenant membership cannot be null");
    Objects.requireNonNull(now, "Timestamp cannot be null");

    return new TenantMembershipJpaEntity(
        membership.id().value(),
        membership.tenantId().value(),
        membership.userId().value(),
        membership.status(),
        now,
        now);
  }

  TenantMembership toDomain(TenantMembershipJpaEntity entity) {
    Objects.requireNonNull(entity, "Entity cannot be null");

    return TenantMembership.reconstitute(
        TenantMembershipId.of(entity.getId()),
        TenantId.of(entity.getTenantId()),
        PlatformUserId.of(entity.getPlatformUserId()),
        entity.getStatus());
  }

  TenantMembershipJpaEntity updateEntity(
      TenantMembership membership, TenantMembershipJpaEntity entity, Instant now) {
    Objects.requireNonNull(membership, "Tenant membership cannot be null");
    Objects.requireNonNull(entity, "Entity cannot be null");
    Objects.requireNonNull(now, "Timestamp cannot be null");

    ensureSameIdentity(membership, entity);

    entity.updateMutableState(membership.status(), now);
    return entity;
  }

  private void ensureSameIdentity(TenantMembership membership, TenantMembershipJpaEntity entity) {
    if (!entity.getId().equals(membership.id().value())) {
      throw new IllegalArgumentException("Tenant membership id mismatch");
    }

    if (!entity.getTenantId().equals(membership.tenantId().value())) {
      throw new IllegalArgumentException("Tenant id mismatch");
    }

    if (!entity.getPlatformUserId().equals(membership.userId().value())) {
      throw new IllegalArgumentException("Platform user id mismatch");
    }
  }
}
