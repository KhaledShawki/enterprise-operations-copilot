package io.github.khaledshawki.eoc.platform.tenantaccess.adapter.out.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.khaledshawki.eoc.tenantaccess.domain.model.PlatformUserId;
import io.github.khaledshawki.eoc.tenantaccess.domain.model.TenantId;
import io.github.khaledshawki.eoc.tenantaccess.domain.model.TenantMembership;
import io.github.khaledshawki.eoc.tenantaccess.domain.model.TenantMembershipId;
import io.github.khaledshawki.eoc.tenantaccess.domain.model.TenantMembershipStatus;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class TenantMembershipPersistenceMapperTest {

  private static final Instant NOW = Instant.parse("2026-07-19T12:00:00Z");

  private static final TenantId TENANT_ID = TenantId.generate();
  private static final PlatformUserId USER_ID = PlatformUserId.generate();

  private final TenantMembershipPersistenceMapper mapper = new TenantMembershipPersistenceMapper();

  @Test
  void shouldMapTenantMembershipToJpaEntity() {
    TenantMembership membership =
        TenantMembership.reconstitute(
            TenantMembershipId.generate(), TENANT_ID, USER_ID, TenantMembershipStatus.ACTIVE);

    TenantMembershipJpaEntity entity = mapper.toEntity(membership, NOW);

    assertEquals(membership.id().value(), entity.getId());
    assertEquals(TENANT_ID.value(), entity.getTenantId());
    assertEquals(USER_ID.value(), entity.getPlatformUserId());
    assertEquals(TenantMembershipStatus.ACTIVE, entity.getStatus());
    assertEquals(NOW, entity.getCreatedAt());
    assertEquals(NOW, entity.getUpdatedAt());
  }

  @Test
  void shouldMapJpaEntityToTenantMembership() {
    TenantMembershipId membershipId = TenantMembershipId.generate();

    TenantMembershipJpaEntity entity =
        new TenantMembershipJpaEntity(
            membershipId.value(),
            TENANT_ID.value(),
            USER_ID.value(),
            TenantMembershipStatus.SUSPENDED,
            NOW,
            NOW);

    TenantMembership membership = mapper.toDomain(entity);

    assertEquals(membershipId, membership.id());
    assertEquals(TENANT_ID, membership.tenantId());
    assertEquals(USER_ID, membership.userId());
    assertEquals(TenantMembershipStatus.SUSPENDED, membership.status());
  }

  @Test
  void shouldUpdateMutableJpaEntityState() {
    TenantMembershipId membershipId = TenantMembershipId.generate();
    Instant createdAt = Instant.parse("2026-07-19T10:00:00Z");
    Instant updatedAt = Instant.parse("2026-07-19T11:00:00Z");

    TenantMembershipJpaEntity entity =
        new TenantMembershipJpaEntity(
            membershipId.value(),
            TENANT_ID.value(),
            USER_ID.value(),
            TenantMembershipStatus.ACTIVE,
            createdAt,
            createdAt);

    TenantMembership updatedMembership =
        TenantMembership.reconstitute(
            membershipId, TENANT_ID, USER_ID, TenantMembershipStatus.SUSPENDED);

    TenantMembershipJpaEntity updatedEntity =
        mapper.updateEntity(updatedMembership, entity, updatedAt);

    assertSame(entity, updatedEntity);
    assertEquals(membershipId.value(), updatedEntity.getId());
    assertEquals(TENANT_ID.value(), updatedEntity.getTenantId());
    assertEquals(USER_ID.value(), updatedEntity.getPlatformUserId());
    assertEquals(TenantMembershipStatus.SUSPENDED, updatedEntity.getStatus());
    assertEquals(createdAt, updatedEntity.getCreatedAt());
    assertEquals(updatedAt, updatedEntity.getUpdatedAt());
  }

  @Test
  void shouldRejectUpdatingEntityWithDifferentMembershipId() {
    TenantMembership membership = TenantMembership.create(TENANT_ID, USER_ID);

    TenantMembershipJpaEntity entity =
        new TenantMembershipJpaEntity(
            TenantMembershipId.generate().value(),
            TENANT_ID.value(),
            USER_ID.value(),
            membership.status(),
            NOW,
            NOW);

    assertThrows(
        IllegalArgumentException.class, () -> mapper.updateEntity(membership, entity, NOW));
  }

  @Test
  void shouldRejectUpdatingEntityWithDifferentTenantId() {
    TenantMembership membership = TenantMembership.create(TENANT_ID, USER_ID);

    TenantMembershipJpaEntity entity =
        new TenantMembershipJpaEntity(
            membership.id().value(),
            TenantId.generate().value(),
            USER_ID.value(),
            membership.status(),
            NOW,
            NOW);

    assertThrows(
        IllegalArgumentException.class, () -> mapper.updateEntity(membership, entity, NOW));
  }

  @Test
  void shouldRejectUpdatingEntityWithDifferentPlatformUserId() {
    TenantMembership membership = TenantMembership.create(TENANT_ID, USER_ID);

    TenantMembershipJpaEntity entity =
        new TenantMembershipJpaEntity(
            membership.id().value(),
            TENANT_ID.value(),
            PlatformUserId.generate().value(),
            membership.status(),
            NOW,
            NOW);

    assertThrows(
        IllegalArgumentException.class, () -> mapper.updateEntity(membership, entity, NOW));
  }

  @Test
  void shouldRejectNullDomainMembership() {
    assertThrows(NullPointerException.class, () -> mapper.toEntity(null, NOW));
  }

  @Test
  void shouldRejectNullJpaEntity() {
    assertThrows(NullPointerException.class, () -> mapper.toDomain(null));
  }

  @Test
  void shouldRejectNullTimestamp() {
    TenantMembership membership = TenantMembership.create(TENANT_ID, USER_ID);

    assertThrows(NullPointerException.class, () -> mapper.toEntity(membership, null));
  }
}
