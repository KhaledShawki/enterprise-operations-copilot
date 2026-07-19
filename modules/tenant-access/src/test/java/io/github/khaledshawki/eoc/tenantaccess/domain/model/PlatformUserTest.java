package io.github.khaledshawki.eoc.tenantaccess.domain.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class PlatformUserTest {

  private static final ExternalIdentity EXTERNAL_IDENTITY =
      ExternalIdentity.of("http://localhost:8180/realms/eoc", "user-123");

  @Test
  void shouldCreateActivePlatformUserWithGeneratedId() {
    PlatformUser user = PlatformUser.create(EXTERNAL_IDENTITY);

    assertNotNull(user.id());
    assertEquals(EXTERNAL_IDENTITY, user.externalIdentity());
    assertEquals(PlatformUserStatus.ACTIVE, user.status());
  }

  @Test
  void shouldReconstituteExistingPlatformUser() {
    PlatformUserId userId = PlatformUserId.generate();

    PlatformUser user =
        PlatformUser.reconstitute(userId, EXTERNAL_IDENTITY, PlatformUserStatus.SUSPENDED);

    assertEquals(userId, user.id());
    assertEquals(EXTERNAL_IDENTITY, user.externalIdentity());
    assertEquals(PlatformUserStatus.SUSPENDED, user.status());
  }

  @Test
  void shouldSuspendActivePlatformUser() {
    PlatformUser user = PlatformUser.create(EXTERNAL_IDENTITY);

    user.suspend();

    assertEquals(PlatformUserStatus.SUSPENDED, user.status());
  }

  @Test
  void shouldActivateSuspendedPlatformUser() {
    PlatformUser user =
        PlatformUser.reconstitute(
            PlatformUserId.generate(), EXTERNAL_IDENTITY, PlatformUserStatus.SUSPENDED);

    user.activate();

    assertEquals(PlatformUserStatus.ACTIVE, user.status());
  }

  @Test
  void shouldRejectSuspendingAlreadySuspendedPlatformUser() {
    PlatformUser user =
        PlatformUser.reconstitute(
            PlatformUserId.generate(), EXTERNAL_IDENTITY, PlatformUserStatus.SUSPENDED);

    IllegalStateException exception = assertThrows(IllegalStateException.class, user::suspend);

    assertEquals("Platform user is already suspended", exception.getMessage());
  }

  @Test
  void shouldRejectActivatingAlreadyActivePlatformUser() {
    PlatformUser user = PlatformUser.create(EXTERNAL_IDENTITY);

    IllegalStateException exception = assertThrows(IllegalStateException.class, user::activate);

    assertEquals("Platform user is already active", exception.getMessage());
  }

  @Test
  void shouldRejectNullExternalIdentityWhenCreatingPlatformUser() {
    assertThrows(NullPointerException.class, () -> PlatformUser.create(null));
  }

  @Test
  void shouldRejectNullIdWhenReconstitutingPlatformUser() {
    assertThrows(
        NullPointerException.class,
        () -> PlatformUser.reconstitute(null, EXTERNAL_IDENTITY, PlatformUserStatus.ACTIVE));
  }

  @Test
  void shouldRejectNullExternalIdentityWhenReconstitutingPlatformUser() {
    assertThrows(
        NullPointerException.class,
        () ->
            PlatformUser.reconstitute(PlatformUserId.generate(), null, PlatformUserStatus.ACTIVE));
  }

  @Test
  void shouldRejectNullStatusWhenReconstitutingPlatformUser() {
    assertThrows(
        NullPointerException.class,
        () -> PlatformUser.reconstitute(PlatformUserId.generate(), EXTERNAL_IDENTITY, null));
  }
}
