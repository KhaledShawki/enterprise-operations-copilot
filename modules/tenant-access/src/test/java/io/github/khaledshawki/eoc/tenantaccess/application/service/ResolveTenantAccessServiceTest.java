package io.github.khaledshawki.eoc.tenantaccess.application.service;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.khaledshawki.eoc.tenantaccess.application.port.in.ResolveTenantAccessQuery;
import io.github.khaledshawki.eoc.tenantaccess.application.port.out.PlatformUserRepository;
import io.github.khaledshawki.eoc.tenantaccess.application.port.out.TenantMembershipRepository;
import io.github.khaledshawki.eoc.tenantaccess.application.port.out.TenantRepository;
import io.github.khaledshawki.eoc.tenantaccess.domain.model.ExternalIdentity;
import io.github.khaledshawki.eoc.tenantaccess.domain.model.PlatformUser;
import io.github.khaledshawki.eoc.tenantaccess.domain.model.PlatformUserId;
import io.github.khaledshawki.eoc.tenantaccess.domain.model.Tenant;
import io.github.khaledshawki.eoc.tenantaccess.domain.model.TenantId;
import io.github.khaledshawki.eoc.tenantaccess.domain.model.TenantKey;
import io.github.khaledshawki.eoc.tenantaccess.domain.model.TenantMembership;
import io.github.khaledshawki.eoc.tenantaccess.domain.model.TenantMembershipId;
import io.github.khaledshawki.eoc.tenantaccess.domain.model.TenantName;
import io.github.khaledshawki.eoc.tenantaccess.domain.model.TenantRoleKey;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ResolveTenantAccessServiceTest {

  private static final String ISSUER = "http://localhost:8180/realms/eoc";
  private static final String SUBJECT = "user-123";
  private static final TenantRoleKey AUDITOR = TenantRoleKey.of("auditor");

  private RecordingPlatformUserRepository userRepository;
  private RecordingTenantRepository tenantRepository;
  private RecordingMembershipRepository membershipRepository;
  private ResolveTenantAccessService service;
  private PlatformUser user;
  private Tenant tenant;
  private TenantMembership membership;

  @BeforeEach
  void setUp() {
    user = PlatformUser.create(ExternalIdentity.of(ISSUER, SUBJECT));
    tenant = Tenant.create(TenantKey.of("alpha"), TenantName.of("Alpha"));
    membership = TenantMembership.create(tenant.id(), user.id());
    membership.replaceRoles(Set.of(AUDITOR));

    userRepository = new RecordingPlatformUserRepository(user);
    tenantRepository = new RecordingTenantRepository(tenant);
    membershipRepository = new RecordingMembershipRepository(membership);
    service =
        new ResolveTenantAccessService(userRepository, tenantRepository, membershipRepository);
  }

  @Test
  void shouldAllowActiveMembershipWithRequiredRole() {
    assertTrue(service.resolve(query(tenant.id(), "auditor")).granted());

    assertLookups(1, 1, 1);
  }

  @Test
  void shouldDenyUnknownOrSuspendedUserWithoutDisclosingTenantState() {
    userRepository.user = null;

    assertFalse(service.resolve(query(tenant.id(), "auditor")).granted());
    assertLookups(1, 0, 0);

    userRepository.user = user;
    user.suspend();

    assertFalse(service.resolve(query(tenant.id(), "auditor")).granted());
    assertLookups(2, 0, 0);
  }

  @Test
  void shouldDenyMissingOrSuspendedTenantWithoutLookingUpMembership() {
    tenantRepository.tenant = null;

    assertFalse(service.resolve(query(tenant.id(), "auditor")).granted());
    assertLookups(1, 1, 0);

    tenantRepository.tenant = tenant;
    tenant.suspend();

    assertFalse(service.resolve(query(tenant.id(), "auditor")).granted());
    assertLookups(2, 2, 0);
  }

  @Test
  void shouldDenyMissingOrSuspendedMembership() {
    membershipRepository.membership = null;

    assertFalse(service.resolve(query(tenant.id(), "auditor")).granted());

    membershipRepository.membership = membership;
    membership.suspend();

    assertFalse(service.resolve(query(tenant.id(), "auditor")).granted());
  }

  @Test
  void shouldDenyMembershipReturnedForAnotherTenantOrUser() {
    Tenant otherTenant = Tenant.create(TenantKey.of("beta"), TenantName.of("Beta"));
    membershipRepository.membership = TenantMembership.create(otherTenant.id(), user.id());

    assertFalse(service.resolve(query(tenant.id(), "auditor")).granted());

    PlatformUser otherUser = PlatformUser.create(ExternalIdentity.of(ISSUER, "other-user"));
    membershipRepository.membership = TenantMembership.create(tenant.id(), otherUser.id());

    assertFalse(service.resolve(query(tenant.id(), "auditor")).granted());
  }

  @Test
  void shouldDenyMissingRoleWithoutRestrictingRoleVocabulary() {
    assertFalse(service.resolve(query(tenant.id(), "operations-manager")).granted());

    membership.replaceRoles(Set.of(TenantRoleKey.of("future-custom-role")));

    assertTrue(service.resolve(query(tenant.id(), "future-custom-role")).granted());
  }

  @Test
  void shouldResolveWithoutPersistingOrMutatingState() {
    Set<TenantRoleKey> rolesBefore = membership.roles();

    service.resolve(query(tenant.id(), "auditor"));

    assertAll(
        () -> assertEquals(0, userRepository.saveCalls),
        () -> assertEquals(0, tenantRepository.saveCalls),
        () -> assertEquals(0, membershipRepository.saveCalls),
        () -> assertEquals(rolesBefore, membership.roles()));
  }

  @Test
  void shouldRejectNullCollaboratorsAndQuery() {
    assertThrows(
        NullPointerException.class,
        () -> new ResolveTenantAccessService(null, tenantRepository, membershipRepository));
    assertThrows(
        NullPointerException.class,
        () -> new ResolveTenantAccessService(userRepository, null, membershipRepository));
    assertThrows(
        NullPointerException.class,
        () -> new ResolveTenantAccessService(userRepository, tenantRepository, null));
    assertThrows(NullPointerException.class, () -> service.resolve(null));
  }

  private static ResolveTenantAccessQuery query(TenantId tenantId, String role) {
    return new ResolveTenantAccessQuery(ISSUER, SUBJECT, tenantId.value(), role);
  }

  private void assertLookups(int users, int tenants, int memberships) {
    assertAll(
        () -> assertEquals(users, userRepository.findCalls),
        () -> assertEquals(tenants, tenantRepository.findCalls),
        () -> assertEquals(memberships, membershipRepository.findCalls));
  }

  private static final class RecordingPlatformUserRepository implements PlatformUserRepository {
    private PlatformUser user;
    private int findCalls;
    private int saveCalls;

    private RecordingPlatformUserRepository(PlatformUser user) {
      this.user = user;
    }

    @Override
    public PlatformUser save(PlatformUser user) {
      saveCalls++;
      this.user = user;
      return user;
    }

    @Override
    public Optional<PlatformUser> findById(PlatformUserId userId) {
      return Optional.empty();
    }

    @Override
    public Optional<PlatformUser> findByExternalIdentity(ExternalIdentity externalIdentity) {
      findCalls++;
      return Optional.ofNullable(user);
    }

    @Override
    public boolean existsByExternalIdentity(ExternalIdentity externalIdentity) {
      return false;
    }
  }

  private static final class RecordingTenantRepository implements TenantRepository {
    private Tenant tenant;
    private int findCalls;
    private int saveCalls;

    private RecordingTenantRepository(Tenant tenant) {
      this.tenant = tenant;
    }

    @Override
    public Tenant save(Tenant tenant) {
      saveCalls++;
      this.tenant = tenant;
      return tenant;
    }

    @Override
    public Optional<Tenant> findById(TenantId tenantId) {
      findCalls++;
      return Optional.ofNullable(tenant);
    }

    @Override
    public boolean existsByKey(TenantKey key) {
      return false;
    }
  }

  private static final class RecordingMembershipRepository implements TenantMembershipRepository {
    private TenantMembership membership;
    private int findCalls;
    private int saveCalls;

    private RecordingMembershipRepository(TenantMembership membership) {
      this.membership = membership;
    }

    @Override
    public TenantMembership save(TenantMembership membership) {
      saveCalls++;
      this.membership = membership;
      return membership;
    }

    @Override
    public Optional<TenantMembership> findById(TenantMembershipId membershipId) {
      return Optional.empty();
    }

    @Override
    public Optional<TenantMembership> findByTenantIdAndUserId(
        TenantId tenantId, PlatformUserId userId) {
      findCalls++;
      return Optional.ofNullable(membership);
    }

    @Override
    public boolean existsByTenantIdAndUserId(TenantId tenantId, PlatformUserId userId) {
      return false;
    }
  }
}
