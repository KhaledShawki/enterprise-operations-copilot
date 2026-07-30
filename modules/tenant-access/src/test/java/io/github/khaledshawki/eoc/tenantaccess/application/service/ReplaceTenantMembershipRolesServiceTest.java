package io.github.khaledshawki.eoc.tenantaccess.application.service;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.khaledshawki.eoc.tenantaccess.application.exception.InvalidTenantRoleKeyException;
import io.github.khaledshawki.eoc.tenantaccess.application.exception.TenantMembershipNotFoundException;
import io.github.khaledshawki.eoc.tenantaccess.application.exception.TenantNotFoundException;
import io.github.khaledshawki.eoc.tenantaccess.application.port.in.ReplaceTenantMembershipRolesCommand;
import io.github.khaledshawki.eoc.tenantaccess.application.port.in.ReplaceTenantMembershipRolesResult;
import io.github.khaledshawki.eoc.tenantaccess.application.port.out.TenantMembershipRepository;
import io.github.khaledshawki.eoc.tenantaccess.application.port.out.TenantMembershipRoleWriteRepository;
import io.github.khaledshawki.eoc.tenantaccess.application.port.out.TenantRepository;
import io.github.khaledshawki.eoc.tenantaccess.domain.model.PlatformUserId;
import io.github.khaledshawki.eoc.tenantaccess.domain.model.Tenant;
import io.github.khaledshawki.eoc.tenantaccess.domain.model.TenantId;
import io.github.khaledshawki.eoc.tenantaccess.domain.model.TenantKey;
import io.github.khaledshawki.eoc.tenantaccess.domain.model.TenantMembership;
import io.github.khaledshawki.eoc.tenantaccess.domain.model.TenantMembershipId;
import io.github.khaledshawki.eoc.tenantaccess.domain.model.TenantMembershipStatus;
import io.github.khaledshawki.eoc.tenantaccess.domain.model.TenantName;
import io.github.khaledshawki.eoc.tenantaccess.domain.model.TenantRoleKey;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ReplaceTenantMembershipRolesServiceTest {

  private static final PlatformUserId PLATFORM_USER_ID =
      PlatformUserId.of(UUID.fromString("00000000-0000-0000-0000-000000000001"));

  private InMemoryTenantRepository tenantRepository;
  private InMemoryTenantMembershipRepository membershipRepository;

  private ReplaceTenantMembershipRolesService service;

  @BeforeEach
  void setUp() {
    tenantRepository = new InMemoryTenantRepository();
    membershipRepository = new InMemoryTenantMembershipRepository();

    service =
        new ReplaceTenantMembershipRolesService(
            tenantRepository, membershipRepository, membershipRepository);
  }

  @Test
  void shouldNormalizeReplaceAndPersistTenantMembershipRoles() {
    Tenant tenant = storeTenant("tenant-one");

    TenantMembership membership =
        storeMembership(TenantMembership.create(tenant.id(), PLATFORM_USER_ID));

    ReplaceTenantMembershipRolesResult result =
        service.replaceRoles(
            new ReplaceTenantMembershipRolesCommand(
                tenant.id().value(),
                membership.id().value(),
                Set.of("tenant-admin", " Tenant-Admin ", "AUDITOR")));

    Set<TenantRoleKey> expectedRoles =
        Set.of(TenantRoleKey.of("tenant-admin"), TenantRoleKey.of("auditor"));

    assertAll(
        () -> assertEquals(membership.id(), result.membershipId()),
        () -> assertEquals(tenant.id(), result.tenantId()),
        () -> assertEquals(PLATFORM_USER_ID, result.platformUserId()),
        () -> assertEquals(TenantMembershipStatus.ACTIVE, result.status()),
        () -> assertEquals(expectedRoles, result.roles()),
        () -> assertEquals(expectedRoles, membership.roles()));

    assertEquals(1, tenantRepository.findByIdCalls());
    assertEquals(1, membershipRepository.findByIdCalls());
    assertEquals(1, membershipRepository.replaceRolesCalls());
    assertSame(membership, membershipRepository.lastRoleWriteMembership());
  }

  @Test
  void shouldClearTenantMembershipRoles() {
    Tenant tenant = storeTenant("tenant-one");

    TenantMembership membership =
        TenantMembership.reconstitute(
            TenantMembershipId.generate(),
            tenant.id(),
            PLATFORM_USER_ID,
            TenantMembershipStatus.ACTIVE,
            Set.of(TenantRoleKey.of("tenant-admin"), TenantRoleKey.of("auditor")));

    storeMembership(membership);

    ReplaceTenantMembershipRolesResult result =
        service.replaceRoles(
            new ReplaceTenantMembershipRolesCommand(
                tenant.id().value(), membership.id().value(), Set.of()));

    assertEquals(Set.of(), result.roles());
    assertEquals(Set.of(), membership.roles());

    assertEquals(1, membershipRepository.replaceRolesCalls());
    assertSame(membership, membershipRepository.lastRoleWriteMembership());
  }

  @Test
  void shouldReplaceRolesOnSuspendedMembershipWithoutChangingStatus() {
    Tenant tenant = storeTenant("tenant-one");

    TenantMembership membership =
        TenantMembership.reconstitute(
            TenantMembershipId.generate(),
            tenant.id(),
            PLATFORM_USER_ID,
            TenantMembershipStatus.ACTIVE,
            Set.of(TenantRoleKey.of("auditor")));

    membership.suspend();
    storeMembership(membership);

    ReplaceTenantMembershipRolesResult result =
        service.replaceRoles(
            new ReplaceTenantMembershipRolesCommand(
                tenant.id().value(), membership.id().value(), Set.of("operations-manager")));

    assertEquals(TenantMembershipStatus.SUSPENDED, result.status());
    assertEquals(TenantMembershipStatus.SUSPENDED, membership.status());
    assertEquals(Set.of(TenantRoleKey.of("operations-manager")), result.roles());
    assertEquals(Set.of(TenantRoleKey.of("operations-manager")), membership.roles());
    assertEquals(1, membershipRepository.replaceRolesCalls());
  }

  @Test
  void shouldRejectMissingTenantWithoutAccessingMembershipRepository() {
    TenantId tenantId = TenantId.of(UUID.fromString("00000000-0000-0000-0000-000000000010"));

    TenantMembershipId membershipId =
        TenantMembershipId.of(UUID.fromString("00000000-0000-0000-0000-000000000011"));

    TenantNotFoundException exception =
        assertThrows(
            TenantNotFoundException.class,
            () ->
                service.replaceRoles(
                    new ReplaceTenantMembershipRolesCommand(
                        tenantId.value(), membershipId.value(), Set.of("tenant-admin"))));

    assertEquals("Tenant " + tenantId.value() + " was not found", exception.getMessage());
    assertEquals(1, tenantRepository.findByIdCalls());
    assertEquals(0, membershipRepository.findByIdCalls());
    assertEquals(0, membershipRepository.replaceRolesCalls());
  }

  @Test
  void shouldRejectMissingTenantMembershipWithoutSaving() {
    Tenant tenant = storeTenant("tenant-one");

    TenantMembershipId membershipId =
        TenantMembershipId.of(UUID.fromString("00000000-0000-0000-0000-000000000011"));

    TenantMembershipNotFoundException exception =
        assertThrows(
            TenantMembershipNotFoundException.class,
            () ->
                service.replaceRoles(
                    new ReplaceTenantMembershipRolesCommand(
                        tenant.id().value(), membershipId.value(), Set.of("tenant-admin"))));

    assertEquals(
        "Tenant membership "
            + membershipId.value()
            + " was not found for tenant "
            + tenant.id().value(),
        exception.getMessage());

    assertEquals(1, tenantRepository.findByIdCalls());
    assertEquals(1, membershipRepository.findByIdCalls());
    assertEquals(0, membershipRepository.replaceRolesCalls());
  }

  @Test
  void shouldHideMembershipBelongingToAnotherTenantWithoutMutation() {
    Tenant requestedTenant = storeTenant("requested-tenant");
    Tenant owningTenant = storeTenant("owning-tenant");

    Set<TenantRoleKey> originalRoles = Set.of(TenantRoleKey.of("auditor"));

    TenantMembership membership =
        TenantMembership.reconstitute(
            TenantMembershipId.generate(),
            owningTenant.id(),
            PLATFORM_USER_ID,
            TenantMembershipStatus.ACTIVE,
            originalRoles);

    storeMembership(membership);

    TenantMembershipNotFoundException exception =
        assertThrows(
            TenantMembershipNotFoundException.class,
            () ->
                service.replaceRoles(
                    new ReplaceTenantMembershipRolesCommand(
                        requestedTenant.id().value(),
                        membership.id().value(),
                        Set.of("tenant-admin"))));

    assertEquals(
        "Tenant membership "
            + membership.id().value()
            + " was not found for tenant "
            + requestedTenant.id().value(),
        exception.getMessage());

    assertEquals(originalRoles, membership.roles());
    assertEquals(0, membershipRepository.replaceRolesCalls());
  }

  @Test
  void shouldRejectInvalidRoleWithoutMutatingOrSavingMembership() {
    Tenant tenant = storeTenant("tenant-one");

    Set<TenantRoleKey> originalRoles = Set.of(TenantRoleKey.of("auditor"));

    TenantMembership membership =
        TenantMembership.reconstitute(
            TenantMembershipId.generate(),
            tenant.id(),
            PLATFORM_USER_ID,
            TenantMembershipStatus.ACTIVE,
            originalRoles);

    storeMembership(membership);

    InvalidTenantRoleKeyException exception =
        assertThrows(
            InvalidTenantRoleKeyException.class,
            () ->
                service.replaceRoles(
                    new ReplaceTenantMembershipRolesCommand(
                        tenant.id().value(), membership.id().value(), Set.of("invalid_role"))));

    assertEquals("Tenant role key has an invalid format", exception.getMessage());
    assertEquals(originalRoles, membership.roles());
    assertEquals(0, membershipRepository.replaceRolesCalls());
  }

  @Test
  void shouldRejectNullCommandWithoutAccessingRepositories() {
    NullPointerException exception =
        assertThrows(NullPointerException.class, () -> service.replaceRoles(null));

    assertEquals("Command cannot be null", exception.getMessage());
    assertEquals(0, tenantRepository.findByIdCalls());
    assertEquals(0, membershipRepository.findByIdCalls());
    assertEquals(0, membershipRepository.replaceRolesCalls());
  }

  @Test
  void shouldRejectNullTenantRepository() {
    NullPointerException exception =
        assertThrows(
            NullPointerException.class,
            () ->
                new ReplaceTenantMembershipRolesService(
                    null, membershipRepository, membershipRepository));

    assertEquals("Tenant repository cannot be null", exception.getMessage());
  }

  @Test
  void shouldRejectNullTenantMembershipRepository() {
    NullPointerException exception =
        assertThrows(
            NullPointerException.class,
            () ->
                new ReplaceTenantMembershipRolesService(
                    tenantRepository, null, membershipRepository));

    assertEquals("Tenant membership repository cannot be null", exception.getMessage());
  }

  @Test
  void shouldRejectNullTenantMembershipRoleWriteRepository() {
    NullPointerException exception =
        assertThrows(
            NullPointerException.class,
            () ->
                new ReplaceTenantMembershipRolesService(
                    tenantRepository, membershipRepository, null));

    assertEquals("Tenant membership role write repository cannot be null", exception.getMessage());
  }

  private Tenant storeTenant(String key) {
    Tenant tenant = Tenant.create(TenantKey.of(key), TenantName.of("Tenant " + key));
    tenantRepository.store(tenant);
    return tenant;
  }

  private TenantMembership storeMembership(TenantMembership membership) {
    membershipRepository.store(membership);
    return membership;
  }

  private static final class InMemoryTenantRepository implements TenantRepository {

    private final Map<TenantId, Tenant> tenants = new HashMap<>();
    private int findByIdCalls;

    void store(Tenant tenant) {
      tenants.put(tenant.id(), tenant);
    }

    @Override
    public Tenant save(Tenant tenant) {
      store(tenant);
      return tenant;
    }

    @Override
    public Optional<Tenant> findById(TenantId tenantId) {
      findByIdCalls++;
      return Optional.ofNullable(tenants.get(tenantId));
    }

    @Override
    public boolean existsByKey(TenantKey key) {
      return tenants.values().stream().map(Tenant::key).anyMatch(key::equals);
    }

    int findByIdCalls() {
      return findByIdCalls;
    }
  }

  private static final class InMemoryTenantMembershipRepository
      implements TenantMembershipRepository, TenantMembershipRoleWriteRepository {

    private final Map<TenantMembershipId, TenantMembership> memberships = new HashMap<>();

    private int findByIdCalls;
    private int replaceRolesCalls;
    private TenantMembership lastRoleWriteMembership;

    void store(TenantMembership membership) {
      memberships.put(membership.id(), membership);
    }

    @Override
    public TenantMembership save(TenantMembership membership) {
      store(membership);
      return membership;
    }

    @Override
    public Optional<TenantMembership> findById(TenantMembershipId membershipId) {
      findByIdCalls++;
      return Optional.ofNullable(memberships.get(membershipId));
    }

    @Override
    public Optional<TenantMembership> findByTenantIdAndUserId(
        TenantId tenantId, PlatformUserId userId) {
      return memberships.values().stream()
          .filter(
              membership ->
                  membership.tenantId().equals(tenantId) && membership.userId().equals(userId))
          .findFirst();
    }

    @Override
    public boolean existsByTenantIdAndUserId(TenantId tenantId, PlatformUserId userId) {
      return findByTenantIdAndUserId(tenantId, userId).isPresent();
    }

    @Override
    public TenantMembership replaceRoles(TenantMembership membership) {
      replaceRolesCalls++;
      lastRoleWriteMembership = membership;
      store(membership);
      return membership;
    }

    int findByIdCalls() {
      return findByIdCalls;
    }

    int replaceRolesCalls() {
      return replaceRolesCalls;
    }

    TenantMembership lastRoleWriteMembership() {
      return lastRoleWriteMembership;
    }
  }
}
