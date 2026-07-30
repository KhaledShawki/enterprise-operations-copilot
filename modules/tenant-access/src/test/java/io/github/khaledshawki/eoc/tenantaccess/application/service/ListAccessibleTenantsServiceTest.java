package io.github.khaledshawki.eoc.tenantaccess.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.khaledshawki.eoc.tenantaccess.application.exception.PlatformUserNotActiveException;
import io.github.khaledshawki.eoc.tenantaccess.application.exception.PlatformUserNotFoundException;
import io.github.khaledshawki.eoc.tenantaccess.application.port.in.AccessibleTenantResult;
import io.github.khaledshawki.eoc.tenantaccess.application.port.in.ListAccessibleTenantsQuery;
import io.github.khaledshawki.eoc.tenantaccess.application.port.in.ListAccessibleTenantsResult;
import io.github.khaledshawki.eoc.tenantaccess.application.port.out.AccessibleTenantProjection;
import io.github.khaledshawki.eoc.tenantaccess.application.port.out.AccessibleTenantQueryRepository;
import io.github.khaledshawki.eoc.tenantaccess.application.port.out.PlatformUserRepository;
import io.github.khaledshawki.eoc.tenantaccess.domain.model.ExternalIdentity;
import io.github.khaledshawki.eoc.tenantaccess.domain.model.PlatformUser;
import io.github.khaledshawki.eoc.tenantaccess.domain.model.PlatformUserId;
import io.github.khaledshawki.eoc.tenantaccess.domain.model.PlatformUserStatus;
import io.github.khaledshawki.eoc.tenantaccess.domain.model.TenantId;
import io.github.khaledshawki.eoc.tenantaccess.domain.model.TenantKey;
import io.github.khaledshawki.eoc.tenantaccess.domain.model.TenantMembershipId;
import io.github.khaledshawki.eoc.tenantaccess.domain.model.TenantMembershipStatus;
import io.github.khaledshawki.eoc.tenantaccess.domain.model.TenantName;
import io.github.khaledshawki.eoc.tenantaccess.domain.model.TenantRoleKey;
import io.github.khaledshawki.eoc.tenantaccess.domain.model.TenantStatus;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ListAccessibleTenantsServiceTest {

  private static final String ISSUER = "http://localhost:8180/realms/eoc";

  private static final String SUBJECT = "user-123";

  private static final ExternalIdentity EXTERNAL_IDENTITY = ExternalIdentity.of(ISSUER, SUBJECT);

  private static final PlatformUserId PLATFORM_USER_ID =
      PlatformUserId.of(UUID.fromString("00000000-0000-0000-0000-000000000001"));

  private static final Set<TenantRoleKey> ALPHA_ROLES =
      Set.of(TenantRoleKey.of("tenant-admin"), TenantRoleKey.of("auditor"));

  private static final Set<TenantRoleKey> BETA_ROLES =
      Set.of(TenantRoleKey.of("operations-manager"));

  private InMemoryPlatformUserRepository platformUserRepository;

  private InMemoryAccessibleTenantQueryRepository accessibleTenantQueryRepository;

  private ListAccessibleTenantsService service;

  @BeforeEach
  void setUp() {
    platformUserRepository = new InMemoryPlatformUserRepository();

    accessibleTenantQueryRepository = new InMemoryAccessibleTenantQueryRepository();

    service =
        new ListAccessibleTenantsService(platformUserRepository, accessibleTenantQueryRepository);
  }

  @Test
  void shouldReturnOnlyActiveAccessibleTenantsSortedByTenantKey() {
    platformUserRepository.add(platformUser(PlatformUserStatus.ACTIVE));

    AccessibleTenantProjection betaTenant =
        projection(
            "00000000-0000-0000-0000-000000000102",
            "00000000-0000-0000-0000-000000000202",
            "beta",
            "Beta Tenant",
            TenantStatus.ACTIVE,
            TenantMembershipStatus.ACTIVE,
            BETA_ROLES);

    AccessibleTenantProjection suspendedMembership =
        projection(
            "00000000-0000-0000-0000-000000000103",
            "00000000-0000-0000-0000-000000000203",
            "suspended-membership",
            "Suspended Membership Tenant",
            TenantStatus.ACTIVE,
            TenantMembershipStatus.SUSPENDED);

    AccessibleTenantProjection suspendedTenant =
        projection(
            "00000000-0000-0000-0000-000000000104",
            "00000000-0000-0000-0000-000000000204",
            "suspended-tenant",
            "Suspended Tenant",
            TenantStatus.SUSPENDED,
            TenantMembershipStatus.ACTIVE);

    AccessibleTenantProjection alphaTenant =
        projection(
            "00000000-0000-0000-0000-000000000101",
            "00000000-0000-0000-0000-000000000201",
            "alpha",
            "Alpha Tenant",
            TenantStatus.ACTIVE,
            TenantMembershipStatus.ACTIVE,
            ALPHA_ROLES);

    accessibleTenantQueryRepository.setProjections(
        List.of(betaTenant, suspendedMembership, suspendedTenant, alphaTenant));

    ListAccessibleTenantsResult result = service.list(query());

    assertEquals(
        List.of(accessibleTenantResult(alphaTenant), accessibleTenantResult(betaTenant)),
        result.tenants());

    assertEquals(1, platformUserRepository.findByExternalIdentityCalls());

    assertEquals(1, accessibleTenantQueryRepository.findAllCalls());

    assertEquals(PLATFORM_USER_ID, accessibleTenantQueryRepository.lastPlatformUserId());

    assertEquals(0, platformUserRepository.saveCalls());
  }

  @Test
  void shouldReturnEmptyResultWhenUserHasNoAccessibleTenants() {
    platformUserRepository.add(platformUser(PlatformUserStatus.ACTIVE));

    accessibleTenantQueryRepository.setProjections(
        List.of(
            projection(
                "00000000-0000-0000-0000-000000000105",
                "00000000-0000-0000-0000-000000000205",
                "inactive-membership",
                "Inactive Membership",
                TenantStatus.ACTIVE,
                TenantMembershipStatus.SUSPENDED),
            projection(
                "00000000-0000-0000-0000-000000000106",
                "00000000-0000-0000-0000-000000000206",
                "inactive-tenant",
                "Inactive Tenant",
                TenantStatus.SUSPENDED,
                TenantMembershipStatus.ACTIVE)));

    ListAccessibleTenantsResult result = service.list(query());

    assertEquals(List.of(), result.tenants());

    assertEquals(1, accessibleTenantQueryRepository.findAllCalls());

    assertEquals(0, platformUserRepository.saveCalls());
  }

  @Test
  void shouldRejectUnknownPlatformUserBeforeQueryingTenantAccess() {
    assertThrows(PlatformUserNotFoundException.class, () -> service.list(query()));

    assertEquals(1, platformUserRepository.findByExternalIdentityCalls());

    assertEquals(0, accessibleTenantQueryRepository.findAllCalls());

    assertEquals(0, platformUserRepository.saveCalls());
  }

  @Test
  void shouldRejectSuspendedPlatformUserBeforeQueryingTenantAccess() {
    platformUserRepository.add(platformUser(PlatformUserStatus.SUSPENDED));

    PlatformUserNotActiveException exception =
        assertThrows(PlatformUserNotActiveException.class, () -> service.list(query()));

    assertEquals(
        "Platform user " + PLATFORM_USER_ID.value() + " is not active", exception.getMessage());

    assertEquals(1, platformUserRepository.findByExternalIdentityCalls());

    assertEquals(0, accessibleTenantQueryRepository.findAllCalls());

    assertEquals(0, platformUserRepository.saveCalls());
  }

  @Test
  void shouldRejectNullQueryBeforeUsingRepositories() {
    NullPointerException exception =
        assertThrows(NullPointerException.class, () -> service.list(null));

    assertEquals("Query cannot be null", exception.getMessage());

    assertEquals(0, platformUserRepository.findByExternalIdentityCalls());

    assertEquals(0, accessibleTenantQueryRepository.findAllCalls());

    assertEquals(0, platformUserRepository.saveCalls());
  }

  @Test
  void shouldRejectNullProjectionList() {
    platformUserRepository.add(platformUser(PlatformUserStatus.ACTIVE));

    accessibleTenantQueryRepository.returnNull();

    NullPointerException exception =
        assertThrows(NullPointerException.class, () -> service.list(query()));

    assertEquals("Accessible tenant projections cannot be null", exception.getMessage());

    assertEquals(1, accessibleTenantQueryRepository.findAllCalls());

    assertEquals(0, platformUserRepository.saveCalls());
  }

  @Test
  void shouldRejectNullPlatformUserRepository() {
    NullPointerException exception =
        assertThrows(
            NullPointerException.class,
            () -> new ListAccessibleTenantsService(null, accessibleTenantQueryRepository));

    assertEquals("Platform user repository cannot be null", exception.getMessage());
  }

  @Test
  void shouldRejectNullAccessibleTenantQueryRepository() {
    NullPointerException exception =
        assertThrows(
            NullPointerException.class,
            () -> new ListAccessibleTenantsService(platformUserRepository, null));

    assertEquals("Accessible tenant query repository cannot be null", exception.getMessage());
  }

  private static ListAccessibleTenantsQuery query() {
    return new ListAccessibleTenantsQuery(ISSUER, SUBJECT);
  }

  private static PlatformUser platformUser(PlatformUserStatus status) {
    return PlatformUser.reconstitute(PLATFORM_USER_ID, EXTERNAL_IDENTITY, status);
  }

  private static AccessibleTenantProjection projection(
      String membershipId,
      String tenantId,
      String key,
      String name,
      TenantStatus tenantStatus,
      TenantMembershipStatus membershipStatus) {
    return new AccessibleTenantProjection(
        TenantMembershipId.of(UUID.fromString(membershipId)),
        TenantId.of(UUID.fromString(tenantId)),
        TenantKey.of(key),
        TenantName.of(name),
        tenantStatus,
        membershipStatus,
        Set.of());
  }

  private static AccessibleTenantProjection projection(
      String membershipId,
      String tenantId,
      String key,
      String name,
      TenantStatus tenantStatus,
      TenantMembershipStatus membershipStatus,
      Set<TenantRoleKey> roles) {
    return new AccessibleTenantProjection(
        TenantMembershipId.of(UUID.fromString(membershipId)),
        TenantId.of(UUID.fromString(tenantId)),
        TenantKey.of(key),
        TenantName.of(name),
        tenantStatus,
        membershipStatus,
        roles);
  }

  private static AccessibleTenantResult accessibleTenantResult(
      AccessibleTenantProjection projection) {
    return new AccessibleTenantResult(
        projection.membershipId(),
        projection.tenantId(),
        projection.key(),
        projection.name(),
        projection.roles());
  }

  private static final class InMemoryPlatformUserRepository implements PlatformUserRepository {

    private final Map<PlatformUserId, PlatformUser> usersById = new HashMap<>();

    private final Map<ExternalIdentity, PlatformUser> usersByExternalIdentity = new HashMap<>();

    private int saveCalls;

    private int findByExternalIdentityCalls;

    void add(PlatformUser user) {
      usersById.put(user.id(), user);

      usersByExternalIdentity.put(user.externalIdentity(), user);
    }

    @Override
    public PlatformUser save(PlatformUser user) {
      saveCalls++;

      add(user);

      return user;
    }

    @Override
    public Optional<PlatformUser> findById(PlatformUserId userId) {
      return Optional.ofNullable(usersById.get(userId));
    }

    @Override
    public Optional<PlatformUser> findByExternalIdentity(ExternalIdentity externalIdentity) {
      findByExternalIdentityCalls++;

      return Optional.ofNullable(usersByExternalIdentity.get(externalIdentity));
    }

    @Override
    public boolean existsByExternalIdentity(ExternalIdentity externalIdentity) {
      return usersByExternalIdentity.containsKey(externalIdentity);
    }

    int saveCalls() {
      return saveCalls;
    }

    int findByExternalIdentityCalls() {
      return findByExternalIdentityCalls;
    }
  }

  private static final class InMemoryAccessibleTenantQueryRepository
      implements AccessibleTenantQueryRepository {

    private List<AccessibleTenantProjection> projections = new ArrayList<>();

    private boolean returnNull;

    private int findAllCalls;

    private PlatformUserId lastPlatformUserId;

    void setProjections(List<AccessibleTenantProjection> projections) {
      this.projections = new ArrayList<>(projections);
    }

    void returnNull() {
      returnNull = true;
    }

    @Override
    public List<AccessibleTenantProjection> findAllByPlatformUserId(PlatformUserId platformUserId) {
      findAllCalls++;

      lastPlatformUserId = platformUserId;

      if (returnNull) {
        return null;
      }

      return List.copyOf(projections);
    }

    int findAllCalls() {
      return findAllCalls;
    }

    PlatformUserId lastPlatformUserId() {
      return lastPlatformUserId;
    }
  }
}
