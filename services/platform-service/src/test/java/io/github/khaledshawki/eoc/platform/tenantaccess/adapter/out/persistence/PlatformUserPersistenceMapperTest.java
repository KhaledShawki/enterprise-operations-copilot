package io.github.khaledshawki.eoc.platform.tenantaccess.adapter.out.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.khaledshawki.eoc.tenantaccess.domain.model.ExternalIdentity;
import io.github.khaledshawki.eoc.tenantaccess.domain.model.PlatformUser;
import io.github.khaledshawki.eoc.tenantaccess.domain.model.PlatformUserId;
import io.github.khaledshawki.eoc.tenantaccess.domain.model.PlatformUserStatus;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class PlatformUserPersistenceMapperTest {

  private static final Instant NOW = Instant.parse("2026-07-19T12:00:00Z");

  private static final ExternalIdentity EXTERNAL_IDENTITY =
      ExternalIdentity.of("http://localhost:8180/realms/eoc", "user-123");

  private final PlatformUserPersistenceMapper mapper = new PlatformUserPersistenceMapper();

  @Test
  void shouldMapPlatformUserToJpaEntity() {
    PlatformUser user =
        PlatformUser.reconstitute(
            PlatformUserId.generate(), EXTERNAL_IDENTITY, PlatformUserStatus.ACTIVE);

    PlatformUserJpaEntity entity = mapper.toEntity(user, NOW);

    assertEquals(user.id().value(), entity.getId());
    assertEquals(EXTERNAL_IDENTITY.issuer(), entity.getIssuer());
    assertEquals(EXTERNAL_IDENTITY.subject(), entity.getSubject());
    assertEquals(PlatformUserStatus.ACTIVE, entity.getStatus());
    assertEquals(NOW, entity.getCreatedAt());
    assertEquals(NOW, entity.getUpdatedAt());
  }

  @Test
  void shouldMapJpaEntityToPlatformUser() {
    PlatformUserId userId = PlatformUserId.generate();

    PlatformUserJpaEntity entity =
        new PlatformUserJpaEntity(
            userId.value(),
            EXTERNAL_IDENTITY.issuer(),
            EXTERNAL_IDENTITY.subject(),
            PlatformUserStatus.SUSPENDED,
            NOW,
            NOW);

    PlatformUser user = mapper.toDomain(entity);

    assertEquals(userId, user.id());
    assertEquals(EXTERNAL_IDENTITY, user.externalIdentity());
    assertEquals(PlatformUserStatus.SUSPENDED, user.status());
  }

  @Test
  void shouldUpdateMutableJpaEntityState() {
    PlatformUserId userId = PlatformUserId.generate();
    Instant createdAt = Instant.parse("2026-07-19T10:00:00Z");
    Instant updatedAt = Instant.parse("2026-07-19T11:00:00Z");

    PlatformUserJpaEntity entity =
        new PlatformUserJpaEntity(
            userId.value(),
            EXTERNAL_IDENTITY.issuer(),
            EXTERNAL_IDENTITY.subject(),
            PlatformUserStatus.ACTIVE,
            createdAt,
            createdAt);

    PlatformUser updatedUser =
        PlatformUser.reconstitute(userId, EXTERNAL_IDENTITY, PlatformUserStatus.SUSPENDED);

    PlatformUserJpaEntity updatedEntity = mapper.updateEntity(updatedUser, entity, updatedAt);

    assertSame(entity, updatedEntity);
    assertEquals(userId.value(), updatedEntity.getId());
    assertEquals(EXTERNAL_IDENTITY.issuer(), updatedEntity.getIssuer());
    assertEquals(EXTERNAL_IDENTITY.subject(), updatedEntity.getSubject());
    assertEquals(PlatformUserStatus.SUSPENDED, updatedEntity.getStatus());
    assertEquals(createdAt, updatedEntity.getCreatedAt());
    assertEquals(updatedAt, updatedEntity.getUpdatedAt());
  }

  @Test
  void shouldRejectUpdatingEntityWithDifferentUserId() {
    PlatformUser user = PlatformUser.create(EXTERNAL_IDENTITY);

    PlatformUserJpaEntity entity =
        new PlatformUserJpaEntity(
            PlatformUserId.generate().value(),
            EXTERNAL_IDENTITY.issuer(),
            EXTERNAL_IDENTITY.subject(),
            user.status(),
            NOW,
            NOW);

    assertThrows(IllegalArgumentException.class, () -> mapper.updateEntity(user, entity, NOW));
  }

  @Test
  void shouldRejectUpdatingEntityWithDifferentIssuer() {
    PlatformUser user = PlatformUser.create(EXTERNAL_IDENTITY);

    PlatformUserJpaEntity entity =
        new PlatformUserJpaEntity(
            user.id().value(),
            "http://localhost:8180/realms/another-realm",
            EXTERNAL_IDENTITY.subject(),
            user.status(),
            NOW,
            NOW);

    assertThrows(IllegalArgumentException.class, () -> mapper.updateEntity(user, entity, NOW));
  }

  @Test
  void shouldRejectUpdatingEntityWithDifferentSubject() {
    PlatformUser user = PlatformUser.create(EXTERNAL_IDENTITY);

    PlatformUserJpaEntity entity =
        new PlatformUserJpaEntity(
            user.id().value(), EXTERNAL_IDENTITY.issuer(), "another-user", user.status(), NOW, NOW);

    assertThrows(IllegalArgumentException.class, () -> mapper.updateEntity(user, entity, NOW));
  }

  @Test
  void shouldRejectNullDomainUser() {
    assertThrows(NullPointerException.class, () -> mapper.toEntity(null, NOW));
  }

  @Test
  void shouldRejectNullJpaEntity() {
    assertThrows(NullPointerException.class, () -> mapper.toDomain(null));
  }

  @Test
  void shouldRejectNullTimestamp() {
    PlatformUser user = PlatformUser.create(EXTERNAL_IDENTITY);

    assertThrows(NullPointerException.class, () -> mapper.toEntity(user, null));
  }
}
