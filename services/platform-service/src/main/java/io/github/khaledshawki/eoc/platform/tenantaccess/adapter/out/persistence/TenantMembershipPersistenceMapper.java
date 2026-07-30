package io.github.khaledshawki.eoc.platform.tenantaccess.adapter.out.persistence;

import io.github.khaledshawki.eoc.tenantaccess.domain.model.PlatformUserId;
import io.github.khaledshawki.eoc.tenantaccess.domain.model.TenantId;
import io.github.khaledshawki.eoc.tenantaccess.domain.model.TenantMembership;
import io.github.khaledshawki.eoc.tenantaccess.domain.model.TenantMembershipId;
import io.github.khaledshawki.eoc.tenantaccess.domain.model.TenantRoleKey;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

final class TenantMembershipPersistenceMapper {

  TenantMembershipJpaEntity toEntity(TenantMembership membership, Instant now) {
    Objects.requireNonNull(membership, "Tenant membership cannot be null");

    Objects.requireNonNull(now, "Timestamp cannot be null");

    return new TenantMembershipJpaEntity(
        membership.id().value(),
        membership.tenantId().value(),
        membership.userId().value(),
        membership.status(),
        toPersistenceRoleKeys(membership.roles()),
        now,
        now);
  }

  TenantMembership toDomain(TenantMembershipJpaEntity entity) {
    Objects.requireNonNull(entity, "Tenant membership entity cannot be null");

    return TenantMembership.reconstitute(
        TenantMembershipId.of(entity.getId()),
        TenantId.of(entity.getTenantId()),
        PlatformUserId.of(entity.getPlatformUserId()),
        entity.getStatus(),
        toDomainRoleKeys(entity.getRoleKeys()));
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

  TenantMembershipJpaEntity replaceRoles(
      TenantMembership membership, TenantMembershipJpaEntity entity, Instant now) {
    Objects.requireNonNull(membership, "Tenant membership cannot be null");

    Objects.requireNonNull(entity, "Entity cannot be null");

    Objects.requireNonNull(now, "Timestamp cannot be null");

    ensureSameIdentity(membership, entity);

    entity.replaceRoleKeys(toPersistenceRoleKeys(membership.roles()), now);

    return entity;
  }

  private static Set<String> toPersistenceRoleKeys(Set<TenantRoleKey> roleKeys) {
    return roleKeys.stream()
        .map(TenantRoleKey::value)
        .collect(Collectors.toCollection(LinkedHashSet::new));
  }

  private static Set<TenantRoleKey> toDomainRoleKeys(Set<String> roleKeys) {
    return roleKeys.stream()
        .map(TenantRoleKey::of)
        .collect(Collectors.toCollection(LinkedHashSet::new));
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
