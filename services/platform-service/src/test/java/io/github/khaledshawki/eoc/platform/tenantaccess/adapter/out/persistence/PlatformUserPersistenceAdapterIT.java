package io.github.khaledshawki.eoc.platform.tenantaccess.adapter.out.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.khaledshawki.eoc.platform.TestcontainersConfiguration;
import io.github.khaledshawki.eoc.tenantaccess.application.exception.ExternalIdentityAlreadyExistsException;
import io.github.khaledshawki.eoc.tenantaccess.application.port.out.PlatformUserRepository;
import io.github.khaledshawki.eoc.tenantaccess.domain.model.ExternalIdentity;
import io.github.khaledshawki.eoc.tenantaccess.domain.model.PlatformUser;
import io.github.khaledshawki.eoc.tenantaccess.domain.model.PlatformUserStatus;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

@SpringBootTest
@Import({TestcontainersConfiguration.class, PersistenceTestClockConfiguration.class})
class PlatformUserPersistenceAdapterIT {

  private static final Instant INITIAL_TIME = Instant.parse("2026-07-19T08:00:00Z");

  private static final ExternalIdentity EXTERNAL_IDENTITY =
      ExternalIdentity.of("http://localhost:8180/realms/eoc", "user-123");

  @Autowired private PlatformUserRepository platformUserRepository;

  @Autowired private SpringDataPlatformUserRepository springDataPlatformUserRepository;

  @Autowired private SpringDataTenantMembershipRepository springDataTenantMembershipRepository;

  @Autowired private MutableClock clock;

  @BeforeEach
  void setUp() {
    springDataTenantMembershipRepository.deleteAllInBatch();
    springDataPlatformUserRepository.deleteAllInBatch();
    clock.setInstant(INITIAL_TIME);
  }

  @Test
  void shouldSaveAndFindPlatformUser() {
    PlatformUser user = PlatformUser.create(EXTERNAL_IDENTITY);

    PlatformUser savedUser = platformUserRepository.save(user);

    assertEquals(user.id(), savedUser.id());
    assertEquals(EXTERNAL_IDENTITY, savedUser.externalIdentity());
    assertEquals(PlatformUserStatus.ACTIVE, savedUser.status());

    PlatformUser loadedById = platformUserRepository.findById(user.id()).orElseThrow();

    assertEquals(savedUser.id(), loadedById.id());
    assertEquals(savedUser.externalIdentity(), loadedById.externalIdentity());
    assertEquals(savedUser.status(), loadedById.status());

    PlatformUser loadedByIdentity =
        platformUserRepository.findByExternalIdentity(EXTERNAL_IDENTITY).orElseThrow();

    assertEquals(savedUser.id(), loadedByIdentity.id());
    assertTrue(platformUserRepository.existsByExternalIdentity(EXTERNAL_IDENTITY));

    PlatformUserJpaEntity storedEntity =
        springDataPlatformUserRepository.findById(user.id().value()).orElseThrow();

    assertEquals(user.id().value(), storedEntity.getId());
    assertEquals(EXTERNAL_IDENTITY.issuer(), storedEntity.getIssuer());
    assertEquals(EXTERNAL_IDENTITY.subject(), storedEntity.getSubject());
    assertEquals(PlatformUserStatus.ACTIVE, storedEntity.getStatus());
    assertEquals(INITIAL_TIME, storedEntity.getCreatedAt());
    assertEquals(INITIAL_TIME, storedEntity.getUpdatedAt());
    assertEquals(0L, storedEntity.getVersion());
  }

  @Test
  void shouldUpdatePlatformUserStatus() {
    PlatformUser user = PlatformUser.create(EXTERNAL_IDENTITY);
    platformUserRepository.save(user);

    Instant updatedTime = Instant.parse("2026-07-19T09:00:00Z");
    clock.setInstant(updatedTime);

    PlatformUser userToUpdate = platformUserRepository.findById(user.id()).orElseThrow();

    userToUpdate.suspend();

    PlatformUser updatedUser = platformUserRepository.save(userToUpdate);

    assertEquals(user.id(), updatedUser.id());
    assertEquals(EXTERNAL_IDENTITY, updatedUser.externalIdentity());
    assertEquals(PlatformUserStatus.SUSPENDED, updatedUser.status());

    PlatformUserJpaEntity updatedEntity =
        springDataPlatformUserRepository.findById(user.id().value()).orElseThrow();

    assertEquals(EXTERNAL_IDENTITY.issuer(), updatedEntity.getIssuer());
    assertEquals(EXTERNAL_IDENTITY.subject(), updatedEntity.getSubject());
    assertEquals(PlatformUserStatus.SUSPENDED, updatedEntity.getStatus());
    assertEquals(INITIAL_TIME, updatedEntity.getCreatedAt());
    assertEquals(updatedTime, updatedEntity.getUpdatedAt());
    assertEquals(1L, updatedEntity.getVersion());
  }

  @Test
  void shouldRejectDuplicateExternalIdentity() {
    PlatformUser first = PlatformUser.create(EXTERNAL_IDENTITY);
    PlatformUser second = PlatformUser.create(EXTERNAL_IDENTITY);

    platformUserRepository.save(first);

    ExternalIdentityAlreadyExistsException exception =
        assertThrows(
            ExternalIdentityAlreadyExistsException.class,
            () -> platformUserRepository.save(second));

    assertInstanceOf(DataIntegrityViolationException.class, exception.getCause());
    assertEquals(1L, springDataPlatformUserRepository.count());
  }

  @Test
  void shouldAllowSameSubjectFromDifferentIssuers() {
    ExternalIdentity firstIdentity =
        ExternalIdentity.of("https://issuer-one.example.com", "shared-subject");
    ExternalIdentity secondIdentity =
        ExternalIdentity.of("https://issuer-two.example.com", "shared-subject");

    platformUserRepository.save(PlatformUser.create(firstIdentity));
    platformUserRepository.save(PlatformUser.create(secondIdentity));

    assertTrue(platformUserRepository.existsByExternalIdentity(firstIdentity));
    assertTrue(platformUserRepository.existsByExternalIdentity(secondIdentity));
    assertEquals(2L, springDataPlatformUserRepository.count());
  }
}
