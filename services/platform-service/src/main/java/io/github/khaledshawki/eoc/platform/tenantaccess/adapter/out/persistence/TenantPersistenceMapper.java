package io.github.khaledshawki.eoc.platform.tenantaccess.adapter.out.persistence;

import io.github.khaledshawki.eoc.tenantaccess.domain.model.Tenant;
import io.github.khaledshawki.eoc.tenantaccess.domain.model.TenantId;
import io.github.khaledshawki.eoc.tenantaccess.domain.model.TenantKey;
import io.github.khaledshawki.eoc.tenantaccess.domain.model.TenantName;
import java.time.Instant;
import java.util.Objects;

final class TenantPersistenceMapper {

  TenantJpaEntity toEntity(Tenant tenant, Instant now) {
    Objects.requireNonNull(tenant, "Tenant cannot be null");
    Objects.requireNonNull(now, "Timestamp cannot be null");

    return new TenantJpaEntity(
        tenant.id().value(),
        tenant.key().value(),
        tenant.name().value(),
        tenant.status(),
        now,
        now);
  }

  Tenant toDomain(TenantJpaEntity entity) {
    Objects.requireNonNull(entity, "Entity cannot be null");

    return Tenant.reconstitute(
        TenantId.of(entity.getId()),
        TenantKey.of(entity.getTenantKey()),
        TenantName.of(entity.getDisplayName()),
        entity.getStatus());
  }

  TenantJpaEntity updateEntity(Tenant tenant, TenantJpaEntity entity, Instant now) {
    Objects.requireNonNull(tenant, "Tenant cannot be null");
    Objects.requireNonNull(entity, "Entity cannot be null");
    Objects.requireNonNull(now, "Timestamp cannot be null");

    ensureSameIdentities(tenant, entity);

    entity.updateMutableState(tenant.name().value(), tenant.status(), now);
    return entity;
  }

  private void ensureSameIdentities(Tenant tenant, TenantJpaEntity entity) {
    if (!entity.getId().equals(tenant.id().value())) {
      throw new IllegalArgumentException("Tenant id mismatch");
    }
    if (!entity.getTenantKey().equals(tenant.key().value())) {
      throw new IllegalArgumentException("Tenant key mismatch");
    }
  }
}
