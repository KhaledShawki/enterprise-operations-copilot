package io.github.khaledshawki.eoc.tenantaccess.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.khaledshawki.eoc.tenantaccess.application.exception.PlatformUserNotActiveException;
import io.github.khaledshawki.eoc.tenantaccess.application.exception.PlatformUserNotFoundException;
import io.github.khaledshawki.eoc.tenantaccess.application.exception.TenantMembershipAlreadyExistsException;
import io.github.khaledshawki.eoc.tenantaccess.application.exception.TenantNotActiveException;
import io.github.khaledshawki.eoc.tenantaccess.application.exception.TenantNotFoundException;
import io.github.khaledshawki.eoc.tenantaccess.application.port.in.AssignTenantMembershipCommand;
import io.github.khaledshawki.eoc.tenantaccess.application.port.in.AssignTenantMembershipResult;
import io.github.khaledshawki.eoc.tenantaccess.application.port.out.PlatformUserRepository;
import io.github.khaledshawki.eoc.tenantaccess.application.port.out.TenantMembershipRepository;
import io.github.khaledshawki.eoc.tenantaccess.application.port.out.TenantRepository;
import io.github.khaledshawki.eoc.tenantaccess.domain.model.ExternalIdentity;
import io.github.khaledshawki.eoc.tenantaccess.domain.model.PlatformUser;
import io.github.khaledshawki.eoc.tenantaccess.domain.model.PlatformUserId;
import io.github.khaledshawki.eoc.tenantaccess.domain.model.PlatformUserStatus;
import io.github.khaledshawki.eoc.tenantaccess.domain.model.Tenant;
import io.github.khaledshawki.eoc.tenantaccess.domain.model.TenantId;
import io.github.khaledshawki.eoc.tenantaccess.domain.model.TenantKey;
import io.github.khaledshawki.eoc.tenantaccess.domain.model.TenantMembership;
import io.github.khaledshawki.eoc.tenantaccess.domain.model.TenantMembershipId;
import io.github.khaledshawki.eoc.tenantaccess.domain.model.TenantMembershipStatus;
import io.github.khaledshawki.eoc.tenantaccess.domain.model.TenantName;
import io.github.khaledshawki.eoc.tenantaccess.domain.model.TenantStatus;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AssignTenantMembershipServiceTest {

  private static final UUID TENANT_UUID = UUID.fromString("00000000-0000-0000-0000-000000000001");

  private static final UUID PLATFORM_USER_UUID =
      UUID.fromString("00000000-0000-0000-0000-000000000002");

  private static final TenantId TENANT_ID = TenantId.of(TENANT_UUID);
  private static final PlatformUserId PLATFORM_USER_ID = PlatformUserId.of(PLATFORM_USER_UUID);

  private static final ExternalIdentity EXTERNAL_IDENTITY =
      ExternalIdentity.of("http://localhost:8180/realms/eoc", "user-123");

  private InMemoryTenantRepository tenantRepository;
  private InMemoryPlatformUserRepository platformUserRepository;
  private InMemoryTenantMembershipRepository membershipRepository;

  private AssignTenantMembershipService service;

  @BeforeEach
  void setUp() {
    tenantRepository = new InMemoryTenantRepository();
    platformUserRepository = new InMemoryPlatformUserRepository();
    membershipRepository = new InMemoryTenantMembershipRepository();

    tenantRepository.save(tenant(TenantStatus.ACTIVE));
    platformUserRepository.save(platformUser(PlatformUserStatus.ACTIVE));

    service =
        new AssignTenantMembershipService(
            tenantRepository, platformUserRepository, membershipRepository);
  }

  @Test
  void shouldCreateAndSaveActiveMembership() {
    AssignTenantMembershipResult result = service.assign(command());

    assertNotNull(result.membershipId());
    assertEquals(TENANT_ID, result.tenantId());
    assertEquals(PLATFORM_USER_ID, result.platformUserId());
    assertEquals(TenantMembershipStatus.ACTIVE, result.status());

    TenantMembership savedMembership =
        membershipRepository.findByTenantIdAndUserId(TENANT_ID, PLATFORM_USER_ID).orElseThrow();

    assertEquals(result.membershipId(), savedMembership.id());
    assertEquals(result.tenantId(), savedMembership.tenantId());
    assertEquals(result.platformUserId(), savedMembership.userId());
    assertEquals(result.status(), savedMembership.status());

    assertEquals(1, membershipRepository.saveCalls());
    assertEquals(1, membershipRepository.size());
  }

  @Test
  void shouldThrowWhenTenantDoesNotExist() {
    UUID unknownTenantId = UUID.fromString("00000000-0000-0000-0000-000000000003");

    assertThrows(
        TenantNotFoundException.class,
        () ->
            service.assign(new AssignTenantMembershipCommand(unknownTenantId, PLATFORM_USER_UUID)));

    assertEquals(0, membershipRepository.saveCalls());
    assertEquals(0, membershipRepository.size());
  }

  @Test
  void shouldThrowWhenPlatformUserDoesNotExist() {
    UUID unknownPlatformUserId = UUID.fromString("00000000-0000-0000-0000-000000000004");

    assertThrows(
        PlatformUserNotFoundException.class,
        () ->
            service.assign(new AssignTenantMembershipCommand(TENANT_UUID, unknownPlatformUserId)));

    assertEquals(0, membershipRepository.saveCalls());
    assertEquals(0, membershipRepository.size());
  }

  @Test
  void shouldRejectSuspendedTenant() {
    tenantRepository.save(tenant(TenantStatus.SUSPENDED));

    assertThrows(TenantNotActiveException.class, () -> service.assign(command()));

    assertEquals(0, membershipRepository.saveCalls());
    assertEquals(0, membershipRepository.size());
  }

  @Test
  void shouldRejectSuspendedPlatformUser() {
    platformUserRepository.save(platformUser(PlatformUserStatus.SUSPENDED));

    assertThrows(PlatformUserNotActiveException.class, () -> service.assign(command()));

    assertEquals(0, membershipRepository.saveCalls());
    assertEquals(0, membershipRepository.size());
  }

  @Test
  void shouldRejectExistingActiveMembership() {
    TenantMembership existingMembership = TenantMembership.create(TENANT_ID, PLATFORM_USER_ID);

    membershipRepository.save(existingMembership);
    membershipRepository.resetSaveCalls();

    assertThrows(TenantMembershipAlreadyExistsException.class, () -> service.assign(command()));

    TenantMembership unchangedMembership =
        membershipRepository.findByTenantIdAndUserId(TENANT_ID, PLATFORM_USER_ID).orElseThrow();

    assertEquals(existingMembership.id(), unchangedMembership.id());
    assertEquals(TenantMembershipStatus.ACTIVE, unchangedMembership.status());
    assertEquals(0, membershipRepository.saveCalls());
    assertEquals(1, membershipRepository.size());
  }

  @Test
  void shouldRejectExistingSuspendedMembershipWithoutReactivatingIt() {
    TenantMembership existingMembership = TenantMembership.create(TENANT_ID, PLATFORM_USER_ID);

    existingMembership.suspend();

    membershipRepository.save(existingMembership);
    membershipRepository.resetSaveCalls();

    assertThrows(TenantMembershipAlreadyExistsException.class, () -> service.assign(command()));

    TenantMembership unchangedMembership =
        membershipRepository.findByTenantIdAndUserId(TENANT_ID, PLATFORM_USER_ID).orElseThrow();

    assertEquals(existingMembership.id(), unchangedMembership.id());
    assertEquals(TenantMembershipStatus.SUSPENDED, unchangedMembership.status());
    assertEquals(0, membershipRepository.saveCalls());
    assertEquals(1, membershipRepository.size());
  }

  @Test
  void shouldPropagateDuplicateDetectedDuringSave() {
    membershipRepository.simulateDuplicateOnNextSave();

    assertThrows(TenantMembershipAlreadyExistsException.class, () -> service.assign(command()));

    assertEquals(1, membershipRepository.saveCalls());
    assertEquals(0, membershipRepository.size());
  }

  @Test
  void shouldRejectNullTenantRepository() {
    NullPointerException exception =
        assertThrows(
            NullPointerException.class,
            () ->
                new AssignTenantMembershipService(
                    null, platformUserRepository, membershipRepository));

    assertEquals("Tenant repository cannot be null", exception.getMessage());
  }

  @Test
  void shouldRejectNullPlatformUserRepository() {
    NullPointerException exception =
        assertThrows(
            NullPointerException.class,
            () -> new AssignTenantMembershipService(tenantRepository, null, membershipRepository));

    assertEquals("Platform user repository cannot be null", exception.getMessage());
  }

  @Test
  void shouldRejectNullMembershipRepository() {
    NullPointerException exception =
        assertThrows(
            NullPointerException.class,
            () ->
                new AssignTenantMembershipService(tenantRepository, platformUserRepository, null));

    assertEquals("Tenant membership repository cannot be null", exception.getMessage());
  }

  @Test
  void shouldRejectNullCommand() {
    NullPointerException exception =
        assertThrows(NullPointerException.class, () -> service.assign(null));

    assertEquals("Command cannot be null", exception.getMessage());
    assertEquals(0, membershipRepository.saveCalls());
    assertEquals(0, membershipRepository.size());
  }

  @Test
  void shouldRejectNullTenantId() {
    NullPointerException exception =
        assertThrows(
            NullPointerException.class,
            () -> new AssignTenantMembershipCommand(null, PLATFORM_USER_UUID));

    assertEquals("Tenant id cannot be null", exception.getMessage());
    assertEquals(0, membershipRepository.saveCalls());
  }

  @Test
  void shouldRejectNullPlatformUserId() {
    NullPointerException exception =
        assertThrows(
            NullPointerException.class, () -> new AssignTenantMembershipCommand(TENANT_UUID, null));

    assertEquals("Platform user id cannot be null", exception.getMessage());
    assertEquals(0, membershipRepository.saveCalls());
  }

  private static AssignTenantMembershipCommand command() {
    return new AssignTenantMembershipCommand(TENANT_UUID, PLATFORM_USER_UUID);
  }

  private static Tenant tenant(TenantStatus status) {
    return Tenant.reconstitute(
        TENANT_ID, TenantKey.of("tenant-key"), TenantName.of("Tenant Name"), status);
  }

  private static PlatformUser platformUser(PlatformUserStatus status) {
    return PlatformUser.reconstitute(PLATFORM_USER_ID, EXTERNAL_IDENTITY, status);
  }

  private static final class InMemoryTenantRepository implements TenantRepository {

    private final Map<TenantId, Tenant> tenants = new HashMap<>();

    @Override
    public Tenant save(Tenant tenant) {
      tenants.put(tenant.id(), tenant);
      return tenant;
    }

    @Override
    public Optional<Tenant> findById(TenantId tenantId) {
      return Optional.ofNullable(tenants.get(tenantId));
    }

    @Override
    public boolean existsByKey(TenantKey key) {
      return tenants.values().stream().map(Tenant::key).anyMatch(key::equals);
    }
  }

  private static final class InMemoryPlatformUserRepository implements PlatformUserRepository {

    private final Map<PlatformUserId, PlatformUser> usersById = new HashMap<>();
    private final Map<ExternalIdentity, PlatformUser> usersByExternalIdentity = new HashMap<>();

    @Override
    public PlatformUser save(PlatformUser user) {
      usersById.put(user.id(), user);
      usersByExternalIdentity.put(user.externalIdentity(), user);
      return user;
    }

    @Override
    public Optional<PlatformUser> findById(PlatformUserId userId) {
      return Optional.ofNullable(usersById.get(userId));
    }

    @Override
    public Optional<PlatformUser> findByExternalIdentity(ExternalIdentity externalIdentity) {
      return Optional.ofNullable(usersByExternalIdentity.get(externalIdentity));
    }

    @Override
    public boolean existsByExternalIdentity(ExternalIdentity externalIdentity) {
      return usersByExternalIdentity.containsKey(externalIdentity);
    }
  }

  private static final class InMemoryTenantMembershipRepository
      implements TenantMembershipRepository {

    private final Map<TenantMembershipId, TenantMembership> membershipsById = new HashMap<>();

    private final Map<MembershipKey, TenantMembership> membershipsByTenantAndUser = new HashMap<>();

    private int saveCalls;
    private boolean duplicateOnNextSave;

    @Override
    public TenantMembership save(TenantMembership membership) {
      saveCalls++;

      if (duplicateOnNextSave) {
        duplicateOnNextSave = false;

        throw new TenantMembershipAlreadyExistsException(
            membership.tenantId(), membership.userId());
      }

      membershipsById.put(membership.id(), membership);
      membershipsByTenantAndUser.put(
          new MembershipKey(membership.tenantId(), membership.userId()), membership);

      return membership;
    }

    @Override
    public Optional<TenantMembership> findById(TenantMembershipId membershipId) {
      return Optional.ofNullable(membershipsById.get(membershipId));
    }

    @Override
    public Optional<TenantMembership> findByTenantIdAndUserId(
        TenantId tenantId, PlatformUserId userId) {
      return Optional.ofNullable(
          membershipsByTenantAndUser.get(new MembershipKey(tenantId, userId)));
    }

    @Override
    public boolean existsByTenantIdAndUserId(TenantId tenantId, PlatformUserId userId) {
      return membershipsByTenantAndUser.containsKey(new MembershipKey(tenantId, userId));
    }

    int saveCalls() {
      return saveCalls;
    }

    int size() {
      return membershipsById.size();
    }

    void resetSaveCalls() {
      saveCalls = 0;
    }

    void simulateDuplicateOnNextSave() {
      duplicateOnNextSave = true;
    }

    private record MembershipKey(TenantId tenantId, PlatformUserId userId) {}
  }
}
