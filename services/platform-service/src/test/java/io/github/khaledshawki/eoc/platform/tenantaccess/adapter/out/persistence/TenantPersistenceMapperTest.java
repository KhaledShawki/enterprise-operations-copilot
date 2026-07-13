package io.github.khaledshawki.eoc.platform.tenantaccess.adapter.out.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.khaledshawki.eoc.tenantaccess.domain.model.Tenant;
import io.github.khaledshawki.eoc.tenantaccess.domain.model.TenantId;
import io.github.khaledshawki.eoc.tenantaccess.domain.model.TenantKey;
import io.github.khaledshawki.eoc.tenantaccess.domain.model.TenantName;
import io.github.khaledshawki.eoc.tenantaccess.domain.model.TenantStatus;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class TenantPersistenceMapperTest {

  private static final Instant NOW = Instant.parse("2026-07-12T00:00:00Z");

  private final TenantPersistenceMapper mapper = new TenantPersistenceMapper();

  @Test
  void shouldMapTenantToJpaEntity() {
    Tenant tenant =
        Tenant.reconstitute(
            TenantId.generate(),
            TenantKey.of("tenant-key"),
            TenantName.of("Tenant Name"),
            TenantStatus.ACTIVE);

    TenantJpaEntity entity = mapper.toEntity(tenant, NOW);

    assertEquals(tenant.id().value(), entity.getId());
    assertEquals(tenant.key().value(), entity.getTenantKey());
    assertEquals(tenant.name().value(), entity.getDisplayName());
    assertEquals(tenant.status(), entity.getStatus());
    assertEquals(NOW, entity.getCreatedAt());
    assertEquals(NOW, entity.getUpdatedAt());
  }

  @Test
  void shouldMapJpaEntityToTenant() {
    TenantJpaEntity entity =
        new TenantJpaEntity(
            TenantId.generate().value(),
            TenantKey.of("tenant-key").value(),
            TenantName.of("Tenant Name").value(),
            TenantStatus.ACTIVE,
            NOW,
            NOW);

    Tenant tenant = mapper.toDomain(entity);

    assertEquals(entity.getId(), tenant.id().value());
    assertEquals(entity.getTenantKey(), tenant.key().value());
    assertEquals(entity.getDisplayName(), tenant.name().value());
    assertEquals(entity.getStatus(), tenant.status());
  }

  @Test
  void shouldRejectNullDomainTenant() {
    assertThrows(NullPointerException.class, () -> mapper.toEntity(null, NOW));
  }

  @Test
  void shouldRejectNullJpaEntity() {
    assertThrows(NullPointerException.class, () -> mapper.toDomain(null));
  }

  @Test
  void shouldRejectNullTimestamp() {
    Tenant tenant = Tenant.create(TenantKey.of("tenant-key"), TenantName.of("Tenant Name"));
    assertThrows(NullPointerException.class, () -> mapper.toEntity(tenant, null));
  }

  @Test
  void shouldUpdateMutableJpaEntityState() {
    TenantId tenantId = TenantId.generate();
    TenantKey tenantKey = TenantKey.of("tenant-key");
    Instant createdAt = Instant.parse("2026-07-13T08:00:00Z");
    Instant updatedAt = Instant.parse("2026-07-13T09:00:00Z");

    TenantJpaEntity entity =
        new TenantJpaEntity(
            tenantId.value(),
            tenantKey.value(),
            "Origin name",
            TenantStatus.ACTIVE,
            createdAt,
            createdAt);

    Tenant updatedTenant =
        Tenant.reconstitute(
            tenantId, tenantKey, TenantName.of("Updated Name"), TenantStatus.SUSPENDED);

    TenantJpaEntity updatedEntity = mapper.updateEntity(updatedTenant, entity, updatedAt);

    assertSame(entity, updatedEntity);

    assertEquals(tenantId.value(), updatedEntity.getId());
    assertEquals(tenantKey.value(), updatedEntity.getTenantKey());
    assertEquals("Updated Name", updatedEntity.getDisplayName());
    assertEquals(TenantStatus.SUSPENDED, updatedEntity.getStatus());
    assertEquals(createdAt, updatedEntity.getCreatedAt());
    assertEquals(updatedAt, updatedEntity.getUpdatedAt());
  }

  @Test
  void shouldRejectUpdatingEntityWithDifferentTenantId() {
    Tenant tenant = Tenant.create(TenantKey.of("tenant-key"), TenantName.of("Tenant Name"));

    TenantJpaEntity entity =
        new TenantJpaEntity(
            TenantId.generate().value(),
            tenant.key().value(),
            tenant.name().value(),
            tenant.status(),
            NOW,
            NOW);

    assertThrows(IllegalArgumentException.class, () -> mapper.updateEntity(tenant, entity, NOW));
  }

  @Test
  void shouldRejectUpdatingEntityWithDifferentTenantKey() {
    Tenant tenant = Tenant.create(TenantKey.of("tenant-key"), TenantName.of("Tenant Name"));
    TenantJpaEntity entity =
        new TenantJpaEntity(
            tenant.id().value(),
            TenantKey.of("tenant-key-2").value(),
            tenant.name().value(),
            tenant.status(),
            NOW,
            NOW);

    assertThrows(IllegalArgumentException.class, () -> mapper.updateEntity(tenant, entity, NOW));
  }
}
