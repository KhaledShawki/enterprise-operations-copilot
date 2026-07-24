package io.github.khaledshawki.eoc.platform.tenantaccess.adapter.out.persistence;

import io.github.khaledshawki.eoc.tenantaccess.domain.model.ExternalIdentity;
import io.github.khaledshawki.eoc.tenantaccess.domain.model.PlatformUser;
import io.github.khaledshawki.eoc.tenantaccess.domain.model.PlatformUserId;
import java.time.Instant;
import java.util.Objects;

final class PlatformUserPersistenceMapper {

  PlatformUserJpaEntity toEntity(PlatformUser user, Instant now) {
    Objects.requireNonNull(user, "Platform user cannot be null");
    Objects.requireNonNull(now, "Timestamp cannot be null");

    return new PlatformUserJpaEntity(
        user.id().value(),
        user.externalIdentity().issuer(),
        user.externalIdentity().subject(),
        user.status(),
        now,
        now);
  }

  PlatformUser toDomain(PlatformUserJpaEntity entity) {
    Objects.requireNonNull(entity, "Entity cannot be null");

    return PlatformUser.reconstitute(
        PlatformUserId.of(entity.getId()),
        ExternalIdentity.of(entity.getIssuer(), entity.getSubject()),
        entity.getStatus());
  }

  PlatformUserJpaEntity updateEntity(PlatformUser user, PlatformUserJpaEntity entity, Instant now) {
    Objects.requireNonNull(user, "Platform user cannot be null");
    Objects.requireNonNull(entity, "Entity cannot be null");
    Objects.requireNonNull(now, "Timestamp cannot be null");

    ensureSameIdentity(user, entity);

    entity.updateMutableState(user.status(), now);
    return entity;
  }

  private void ensureSameIdentity(PlatformUser user, PlatformUserJpaEntity entity) {
    if (!entity.getId().equals(user.id().value())) {
      throw new IllegalArgumentException("Platform user id mismatch");
    }

    if (!entity.getIssuer().equals(user.externalIdentity().issuer())) {
      throw new IllegalArgumentException("External identity issuer mismatch");
    }

    if (!entity.getSubject().equals(user.externalIdentity().subject())) {
      throw new IllegalArgumentException("External identity subject mismatch");
    }
  }
}
