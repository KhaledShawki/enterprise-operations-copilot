package io.github.khaledshawki.eoc.tenantaccess.application.service;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.khaledshawki.eoc.tenantaccess.application.exception.TenantMembershipNotFoundException;
import io.github.khaledshawki.eoc.tenantaccess.application.exception.TenantNotFoundException;
import io.github.khaledshawki.eoc.tenantaccess.application.port.in.GetTenantMembershipQuery;
import io.github.khaledshawki.eoc.tenantaccess.application.port.in.GetTenantMembershipResult;
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
import io.github.khaledshawki.eoc.tenantaccess.domain.model.TenantRoleKey;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GetTenantMembershipServiceTest {

  private static final PlatformUserId PLATFORM_USER_ID =
      PlatformUserId.of(UUID.fromString("00000000-0000-0000-0000-000000000001"));

  private InMemoryTenantRepository tenantRepository;
  private InMemoryTenantMembershipRepository membershipRepository;

  private GetTenantMembershipService service;

  @BeforeEach
  void setUp() {
    tenantRepository = new InMemoryTenantRepository();
    membershipRepository = new InMemoryTenantMembershipRepository();

    service = new GetTenantMembershipService(tenantRepository, membershipRepository);
  }

  @Test
  void shouldReturnActiveTenantMembership() {
    Tenant tenant = saveTenant("tenant-one");

    TenantMembership membership = TenantMembership.create(tenant.id(), PLATFORM_USER_ID);

    membership.replaceRoles(Set.of(TenantRoleKey.of("tenant-admin"), TenantRoleKey.of("auditor")));

    membershipRepository.save(membership);

    GetTenantMembershipResult result =
        service.get(new GetTenantMembershipQuery(tenant.id().value(), membership.id().value()));

    assertAll(
        () -> assertEquals(membership.id(), result.membershipId()),
        () -> assertEquals(tenant.id(), result.tenantId()),
        () -> assertEquals(PLATFORM_USER_ID, result.platformUserId()),
        () -> assertEquals(TenantMembershipStatus.ACTIVE, result.status()),
        () -> assertEquals(membership.roles(), result.roles()));

    assertEquals(1, tenantRepository.findByIdCalls());
    assertEquals(1, membershipRepository.findByIdCalls());
  }

  @Test
  void shouldReturnSuspendedTenantMembership() {
    Tenant tenant = saveTenant("tenant-one");

    TenantMembership membership = TenantMembership.create(tenant.id(), PLATFORM_USER_ID);

    membership.suspend();
    membershipRepository.save(membership);

    GetTenantMembershipResult result =
        service.get(new GetTenantMembershipQuery(tenant.id().value(), membership.id().value()));

    assertAll(
        () -> assertEquals(membership.id(), result.membershipId()),
        () -> assertEquals(tenant.id(), result.tenantId()),
        () -> assertEquals(PLATFORM_USER_ID, result.platformUserId()),
        () -> assertEquals(TenantMembershipStatus.SUSPENDED, result.status()));

    assertEquals(1, tenantRepository.findByIdCalls());
    assertEquals(1, membershipRepository.findByIdCalls());
  }

  @Test
  void shouldRejectMissingTenant() {
    TenantId tenantId = TenantId.of(UUID.fromString("00000000-0000-0000-0000-000000000010"));

    TenantMembershipId membershipId =
        TenantMembershipId.of(UUID.fromString("00000000-0000-0000-0000-000000000011"));

    TenantNotFoundException exception =
        assertThrows(
            TenantNotFoundException.class,
            () ->
                service.get(new GetTenantMembershipQuery(tenantId.value(), membershipId.value())));

    assertEquals("Tenant " + tenantId.value() + " was not found", exception.getMessage());

    assertEquals(1, tenantRepository.findByIdCalls());
    assertEquals(0, membershipRepository.findByIdCalls());
  }

  @Test
  void shouldRejectMissingMembership() {
    Tenant tenant = saveTenant("tenant-one");

    TenantMembershipId membershipId =
        TenantMembershipId.of(UUID.fromString("00000000-0000-0000-0000-000000000011"));

    TenantMembershipNotFoundException exception =
        assertThrows(
            TenantMembershipNotFoundException.class,
            () ->
                service.get(
                    new GetTenantMembershipQuery(tenant.id().value(), membershipId.value())));

    assertEquals(
        "Tenant membership "
            + membershipId.value()
            + " was not found for tenant "
            + tenant.id().value(),
        exception.getMessage());

    assertEquals(1, tenantRepository.findByIdCalls());
    assertEquals(1, membershipRepository.findByIdCalls());
  }

  @Test
  void shouldHideMembershipBelongingToAnotherTenant() {
    Tenant requestedTenant = saveTenant("requested-tenant");
    Tenant owningTenant = saveTenant("owning-tenant");

    TenantMembership membership =
        membershipRepository.save(TenantMembership.create(owningTenant.id(), PLATFORM_USER_ID));

    TenantMembershipNotFoundException exception =
        assertThrows(
            TenantMembershipNotFoundException.class,
            () ->
                service.get(
                    new GetTenantMembershipQuery(
                        requestedTenant.id().value(), membership.id().value())));

    assertEquals(
        "Tenant membership "
            + membership.id().value()
            + " was not found for tenant "
            + requestedTenant.id().value(),
        exception.getMessage());

    assertEquals(1, tenantRepository.findByIdCalls());
    assertEquals(1, membershipRepository.findByIdCalls());
  }

  @Test
  void shouldRejectNullQuery() {
    NullPointerException exception =
        assertThrows(NullPointerException.class, () -> service.get(null));

    assertEquals("Query cannot be null", exception.getMessage());
    assertEquals(0, tenantRepository.findByIdCalls());
    assertEquals(0, membershipRepository.findByIdCalls());
  }

  @Test
  void shouldRejectNullTenantRepository() {
    NullPointerException exception =
        assertThrows(
            NullPointerException.class,
            () -> new GetTenantMembershipService(null, membershipRepository));

    assertEquals("Tenant repository cannot be null", exception.getMessage());
  }

  @Test
  void shouldRejectNullTenantMembershipRepository() {
    NullPointerException exception =
        assertThrows(
            NullPointerException.class,
            () -> new GetTenantMembershipService(tenantRepository, null));

    assertEquals("Tenant membership repository cannot be null", exception.getMessage());
  }

  private Tenant saveTenant(String key) {
    return tenantRepository.save(Tenant.create(TenantKey.of(key), TenantName.of("Tenant " + key)));
  }

  private static final class InMemoryTenantRepository implements TenantRepository {

    private final Map<TenantId, Tenant> tenants = new HashMap<>();

    private int findByIdCalls;

    @Override
    public Tenant save(Tenant tenant) {
      tenants.put(tenant.id(), tenant);
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

    @Override
    public TenantMembership save(TenantMembership membership) {
      memberships.put(membership.id(), membership);
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
  }
}
