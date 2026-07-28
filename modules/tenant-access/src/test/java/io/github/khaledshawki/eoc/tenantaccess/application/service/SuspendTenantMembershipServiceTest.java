package io.github.khaledshawki.eoc.tenantaccess.application.service;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.khaledshawki.eoc.tenantaccess.application.exception.TenantMembershipAlreadySuspendedException;
import io.github.khaledshawki.eoc.tenantaccess.application.exception.TenantMembershipNotFoundException;
import io.github.khaledshawki.eoc.tenantaccess.application.exception.TenantNotFoundException;
import io.github.khaledshawki.eoc.tenantaccess.application.port.in.SuspendTenantMembershipCommand;
import io.github.khaledshawki.eoc.tenantaccess.application.port.in.SuspendTenantMembershipResult;
import io.github.khaledshawki.eoc.tenantaccess.application.port.out.TenantMembershipRepository;
import io.github.khaledshawki.eoc.tenantaccess.application.port.out.TenantRepository;
import io.github.khaledshawki.eoc.tenantaccess.domain.model.PlatformUserId;
import io.github.khaledshawki.eoc.tenantaccess.domain.model.Tenant;
import io.github.khaledshawki.eoc.tenantaccess.domain.model.TenantId;
import io.github.khaledshawki.eoc.tenantaccess.domain.model.TenantKey;
import io.github.khaledshawki.eoc.tenantaccess.domain.model.TenantMembership;
import io.github.khaledshawki.eoc.tenantaccess.domain.model.TenantMembershipId;
import io.github.khaledshawki.eoc.tenantaccess.domain.model.TenantMembershipStatus;
import io.github.khaledshawki.eoc.tenantaccess.domain.model.TenantName;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SuspendTenantMembershipServiceTest {

  private static final PlatformUserId PLATFORM_USER_ID =
      PlatformUserId.of(UUID.fromString("00000000-0000-0000-0000-000000000001"));

  private InMemoryTenantRepository tenantRepository;
  private InMemoryTenantMembershipRepository membershipRepository;

  private SuspendTenantMembershipService service;

  @BeforeEach
  void setUp() {
    tenantRepository = new InMemoryTenantRepository();
    membershipRepository = new InMemoryTenantMembershipRepository();

    service = new SuspendTenantMembershipService(tenantRepository, membershipRepository);
  }

  @Test
  void shouldSuspendAndPersistActiveTenantMembership() {
    Tenant tenant = storeTenant("tenant-one");

    TenantMembership membership =
        storeMembership(TenantMembership.create(tenant.id(), PLATFORM_USER_ID));

    SuspendTenantMembershipResult result =
        service.suspend(
            new SuspendTenantMembershipCommand(tenant.id().value(), membership.id().value()));

    assertAll(
        () -> assertEquals(membership.id(), result.membershipId()),
        () -> assertEquals(tenant.id(), result.tenantId()),
        () -> assertEquals(PLATFORM_USER_ID, result.platformUserId()),
        () -> assertEquals(TenantMembershipStatus.SUSPENDED, result.status()),
        () -> assertEquals(TenantMembershipStatus.SUSPENDED, membership.status()));

    assertEquals(1, tenantRepository.findByIdCalls());
    assertEquals(1, membershipRepository.findByIdCalls());
    assertEquals(1, membershipRepository.saveCalls());
    assertSame(membership, membershipRepository.lastSavedMembership());
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
                service.suspend(
                    new SuspendTenantMembershipCommand(tenantId.value(), membershipId.value())));

    assertEquals("Tenant " + tenantId.value() + " was not found", exception.getMessage());

    assertEquals(1, tenantRepository.findByIdCalls());
    assertEquals(0, membershipRepository.findByIdCalls());
    assertEquals(0, membershipRepository.saveCalls());
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
                service.suspend(
                    new SuspendTenantMembershipCommand(tenant.id().value(), membershipId.value())));

    assertEquals(
        "Tenant membership "
            + membershipId.value()
            + " was not found for tenant "
            + tenant.id().value(),
        exception.getMessage());

    assertEquals(1, tenantRepository.findByIdCalls());
    assertEquals(1, membershipRepository.findByIdCalls());
    assertEquals(0, membershipRepository.saveCalls());
  }

  @Test
  void shouldHideMembershipBelongingToAnotherTenantWithoutSaving() {
    Tenant requestedTenant = storeTenant("requested-tenant");

    Tenant owningTenant = storeTenant("owning-tenant");

    TenantMembership membership =
        storeMembership(TenantMembership.create(owningTenant.id(), PLATFORM_USER_ID));

    TenantMembershipNotFoundException exception =
        assertThrows(
            TenantMembershipNotFoundException.class,
            () ->
                service.suspend(
                    new SuspendTenantMembershipCommand(
                        requestedTenant.id().value(), membership.id().value())));

    assertEquals(
        "Tenant membership "
            + membership.id().value()
            + " was not found for tenant "
            + requestedTenant.id().value(),
        exception.getMessage());

    assertEquals(TenantMembershipStatus.ACTIVE, membership.status());

    assertEquals(1, tenantRepository.findByIdCalls());
    assertEquals(1, membershipRepository.findByIdCalls());
    assertEquals(0, membershipRepository.saveCalls());
  }

  @Test
  void shouldRejectAlreadySuspendedTenantMembershipWithoutSaving() {
    Tenant tenant = storeTenant("tenant-one");

    TenantMembership membership = TenantMembership.create(tenant.id(), PLATFORM_USER_ID);

    membership.suspend();
    storeMembership(membership);

    TenantMembershipAlreadySuspendedException exception =
        assertThrows(
            TenantMembershipAlreadySuspendedException.class,
            () ->
                service.suspend(
                    new SuspendTenantMembershipCommand(
                        tenant.id().value(), membership.id().value())));

    assertEquals(
        "Tenant membership "
            + membership.id().value()
            + " is already suspended for tenant "
            + tenant.id().value(),
        exception.getMessage());

    assertEquals(TenantMembershipStatus.SUSPENDED, membership.status());

    assertEquals(1, tenantRepository.findByIdCalls());
    assertEquals(1, membershipRepository.findByIdCalls());
    assertEquals(0, membershipRepository.saveCalls());
  }

  @Test
  void shouldRejectNullCommandWithoutAccessingRepositories() {
    NullPointerException exception =
        assertThrows(NullPointerException.class, () -> service.suspend(null));

    assertEquals("Command cannot be null", exception.getMessage());

    assertEquals(0, tenantRepository.findByIdCalls());
    assertEquals(0, membershipRepository.findByIdCalls());
    assertEquals(0, membershipRepository.saveCalls());
  }

  @Test
  void shouldRejectNullTenantRepository() {
    NullPointerException exception =
        assertThrows(
            NullPointerException.class,
            () -> new SuspendTenantMembershipService(null, membershipRepository));

    assertEquals("Tenant repository cannot be null", exception.getMessage());
  }

  @Test
  void shouldRejectNullTenantMembershipRepository() {
    NullPointerException exception =
        assertThrows(
            NullPointerException.class,
            () -> new SuspendTenantMembershipService(tenantRepository, null));

    assertEquals("Tenant membership repository cannot be null", exception.getMessage());
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
      implements TenantMembershipRepository {

    private final Map<TenantMembershipId, TenantMembership> memberships = new HashMap<>();

    private int findByIdCalls;
    private int saveCalls;
    private TenantMembership lastSavedMembership;

    void store(TenantMembership membership) {
      memberships.put(membership.id(), membership);
    }

    @Override
    public TenantMembership save(TenantMembership membership) {
      saveCalls++;
      lastSavedMembership = membership;
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

    int findByIdCalls() {
      return findByIdCalls;
    }

    int saveCalls() {
      return saveCalls;
    }

    TenantMembership lastSavedMembership() {
      return lastSavedMembership;
    }
  }
}
