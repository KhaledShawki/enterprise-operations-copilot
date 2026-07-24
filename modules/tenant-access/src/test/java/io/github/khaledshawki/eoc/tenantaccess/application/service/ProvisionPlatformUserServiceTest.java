package io.github.khaledshawki.eoc.tenantaccess.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.khaledshawki.eoc.tenantaccess.application.exception.ExternalIdentityAlreadyExistsException;
import io.github.khaledshawki.eoc.tenantaccess.application.port.in.ProvisionPlatformUserCommand;
import io.github.khaledshawki.eoc.tenantaccess.application.port.in.ProvisionPlatformUserResult;
import io.github.khaledshawki.eoc.tenantaccess.application.port.out.PlatformUserRepository;
import io.github.khaledshawki.eoc.tenantaccess.domain.model.ExternalIdentity;
import io.github.khaledshawki.eoc.tenantaccess.domain.model.PlatformUser;
import io.github.khaledshawki.eoc.tenantaccess.domain.model.PlatformUserId;
import io.github.khaledshawki.eoc.tenantaccess.domain.model.PlatformUserStatus;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ProvisionPlatformUserServiceTest {

  private static final String ISSUER = "http://localhost:8180/realms/eoc";
  private static final String SUBJECT = "user-123";
  private static final ExternalIdentity EXTERNAL_IDENTITY = ExternalIdentity.of(ISSUER, SUBJECT);

  private InMemoryPlatformUserRepository repository;
  private ProvisionPlatformUserService service;

  @BeforeEach
  void setUp() {
    repository = new InMemoryPlatformUserRepository();
    service = new ProvisionPlatformUserService(repository);
  }

  @Test
  void shouldReturnExistingPlatformUserWithoutSaving() {
    PlatformUser existingUser =
        PlatformUser.reconstitute(
            PlatformUserId.generate(), EXTERNAL_IDENTITY, PlatformUserStatus.SUSPENDED);

    repository.save(existingUser);
    repository.resetSaveCalls();

    ProvisionPlatformUserResult result =
        service.provision(new ProvisionPlatformUserCommand(ISSUER, SUBJECT));

    assertEquals(existingUser.id(), result.userId());
    assertEquals(EXTERNAL_IDENTITY, result.externalIdentity());
    assertEquals(PlatformUserStatus.SUSPENDED, result.status());
    assertEquals(0, repository.saveCalls());
    assertEquals(1, repository.size());

    assertFalse(result.created());
  }

  @Test
  void shouldCreateAndSaveActivePlatformUserWhenIdentityDoesNotExist() {
    ProvisionPlatformUserResult result =
        service.provision(new ProvisionPlatformUserCommand(ISSUER, SUBJECT));

    assertEquals(EXTERNAL_IDENTITY, result.externalIdentity());
    assertEquals(PlatformUserStatus.ACTIVE, result.status());
    assertEquals(1, repository.saveCalls());
    assertEquals(1, repository.size());

    PlatformUser savedUser = repository.findByExternalIdentity(EXTERNAL_IDENTITY).orElseThrow();

    assertEquals(result.userId(), savedUser.id());
    assertEquals(result.externalIdentity(), savedUser.externalIdentity());
    assertEquals(result.status(), savedUser.status());

    assertTrue(result.created());
  }

  @Test
  void shouldRejectNullRepository() {
    NullPointerException exception =
        assertThrows(NullPointerException.class, () -> new ProvisionPlatformUserService(null));

    assertEquals("Platform user repository cannot be null", exception.getMessage());
  }

  @Test
  void shouldRejectNullCommand() {
    NullPointerException exception =
        assertThrows(NullPointerException.class, () -> service.provision(null));

    assertEquals("Command cannot be null", exception.getMessage());
    assertEquals(0, repository.size());
  }

  @Test
  void shouldReturnConcurrentWinnerWhenSaveDetectsDuplicateIdentity() {
    PlatformUser concurrentWinner = PlatformUser.create(EXTERNAL_IDENTITY);

    repository.simulateDuplicateOnNextSave(concurrentWinner);

    ProvisionPlatformUserResult result =
        service.provision(new ProvisionPlatformUserCommand(ISSUER, SUBJECT));

    assertEquals(concurrentWinner.id(), result.userId());
    assertEquals(concurrentWinner.externalIdentity(), result.externalIdentity());
    assertEquals(concurrentWinner.status(), result.status());
    assertEquals(1, repository.saveCalls());
    assertEquals(1, repository.size());

    assertFalse(result.created());
  }

  @Test
  void shouldRethrowDuplicateExceptionWhenConcurrentWinnerCannotBeLoaded() {
    repository.simulateDuplicateOnNextSaveWithoutWinner();

    assertThrows(
        ExternalIdentityAlreadyExistsException.class,
        () -> service.provision(new ProvisionPlatformUserCommand(ISSUER, SUBJECT)));

    assertEquals(1, repository.saveCalls());
    assertEquals(0, repository.size());
  }

  @Test
  void shouldRejectNullIssuer() {
    assertThrows(
        NullPointerException.class,
        () -> service.provision(new ProvisionPlatformUserCommand(null, SUBJECT)));

    assertEquals(0, repository.saveCalls());
    assertEquals(0, repository.size());
  }

  @Test
  void shouldRejectBlankIssuer() {
    assertThrows(
        IllegalArgumentException.class,
        () -> service.provision(new ProvisionPlatformUserCommand(" ", SUBJECT)));

    assertEquals(0, repository.saveCalls());
    assertEquals(0, repository.size());
  }

  @Test
  void shouldRejectNullSubject() {
    assertThrows(
        NullPointerException.class,
        () -> service.provision(new ProvisionPlatformUserCommand(ISSUER, null)));

    assertEquals(0, repository.saveCalls());
    assertEquals(0, repository.size());
  }

  @Test
  void shouldRejectBlankSubject() {
    assertThrows(
        IllegalArgumentException.class,
        () -> service.provision(new ProvisionPlatformUserCommand(ISSUER, " ")));

    assertEquals(0, repository.saveCalls());
    assertEquals(0, repository.size());
  }

  private static final class InMemoryPlatformUserRepository implements PlatformUserRepository {

    private final Map<PlatformUserId, PlatformUser> usersById = new HashMap<>();
    private final Map<ExternalIdentity, PlatformUser> usersByIdentity = new HashMap<>();

    private int saveCalls;

    private boolean duplicateOnNextSave;
    private PlatformUser concurrentWinner;

    @Override
    public PlatformUser save(PlatformUser user) {
      saveCalls++;

      if (duplicateOnNextSave) {
        duplicateOnNextSave = false;

        PlatformUser winner = concurrentWinner;
        concurrentWinner = null;

        if (winner != null) {
          store(winner);
        }

        throw new ExternalIdentityAlreadyExistsException(user.externalIdentity());
      }

      store(user);
      return user;
    }

    @Override
    public Optional<PlatformUser> findById(PlatformUserId userId) {
      return Optional.ofNullable(usersById.get(userId));
    }

    @Override
    public Optional<PlatformUser> findByExternalIdentity(ExternalIdentity externalIdentity) {
      return Optional.ofNullable(usersByIdentity.get(externalIdentity));
    }

    @Override
    public boolean existsByExternalIdentity(ExternalIdentity externalIdentity) {
      return usersByIdentity.containsKey(externalIdentity);
    }

    int saveCalls() {
      return saveCalls;
    }

    int size() {
      return usersById.size();
    }

    void resetSaveCalls() {
      saveCalls = 0;
    }

    void simulateDuplicateOnNextSave(PlatformUser winner) {
      duplicateOnNextSave = true;
      concurrentWinner = winner;
    }

    void simulateDuplicateOnNextSaveWithoutWinner() {
      duplicateOnNextSave = true;
      concurrentWinner = null;
    }

    private void store(PlatformUser user) {
      usersById.put(user.id(), user);
      usersByIdentity.put(user.externalIdentity(), user);
    }
  }
}
