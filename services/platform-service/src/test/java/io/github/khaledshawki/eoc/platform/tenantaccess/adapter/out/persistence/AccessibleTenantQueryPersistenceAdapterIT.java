package io.github.khaledshawki.eoc.platform.tenantaccess.adapter.out.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.khaledshawki.eoc.platform.TestcontainersConfiguration;
import io.github.khaledshawki.eoc.tenantaccess.application.port.out.AccessibleTenantProjection;
import io.github.khaledshawki.eoc.tenantaccess.application.port.out.AccessibleTenantQueryRepository;
import io.github.khaledshawki.eoc.tenantaccess.application.port.out.PlatformUserRepository;
import io.github.khaledshawki.eoc.tenantaccess.application.port.out.TenantMembershipRepository;
import io.github.khaledshawki.eoc.tenantaccess.application.port.out.TenantRepository;
import io.github.khaledshawki.eoc.tenantaccess.domain.model.ExternalIdentity;
import io.github.khaledshawki.eoc.tenantaccess.domain.model.PlatformUser;
import io.github.khaledshawki.eoc.tenantaccess.domain.model.Tenant;
import io.github.khaledshawki.eoc.tenantaccess.domain.model.TenantKey;
import io.github.khaledshawki.eoc.tenantaccess.domain.model.TenantMembership;
import io.github.khaledshawki.eoc.tenantaccess.domain.model.TenantMembershipStatus;
import io.github.khaledshawki.eoc.tenantaccess.domain.model.TenantName;
import io.github.khaledshawki.eoc.tenantaccess.domain.model.TenantStatus;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import({TestcontainersConfiguration.class, PersistenceTestClockConfiguration.class})
class AccessibleTenantQueryPersistenceAdapterIT {

  @Autowired private AccessibleTenantQueryRepository accessibleTenantQueryRepository;

  @Autowired private TenantRepository tenantRepository;

  @Autowired private PlatformUserRepository platformUserRepository;

  @Autowired private TenantMembershipRepository tenantMembershipRepository;

  @Autowired private SpringDataTenantMembershipRepository springDataMembershipRepository;

  @Autowired private SpringDataPlatformUserRepository springDataPlatformUserRepository;

  @Autowired private SpringDataTenantRepository springDataTenantRepository;

  @BeforeEach
  void setUp() {
    springDataMembershipRepository.deleteAllInBatch();
    springDataPlatformUserRepository.deleteAllInBatch();
    springDataTenantRepository.deleteAllInBatch();
  }

  @Test
  void shouldReturnTenantAccessRowsForOnlyTheRequestedPlatformUser() {
    PlatformUser requestedUser = createPlatformUser("requested-user");

    PlatformUser otherUser = createPlatformUser("other-user");

    Tenant betaTenant = createTenant("beta", "Beta Tenant");

    Tenant alphaTenant = createTenant("alpha", "Alpha Tenant");

    Tenant suspendedTenant = createTenant("suspended-tenant", "Suspended Tenant");

    suspendedTenant.suspend();
    tenantRepository.save(suspendedTenant);

    TenantMembership betaMembership = createMembership(betaTenant, requestedUser);

    TenantMembership alphaMembership = createMembership(alphaTenant, requestedUser);

    TenantMembership suspendedMembership = createMembership(suspendedTenant, requestedUser);

    suspendedMembership.suspend();
    tenantMembershipRepository.save(suspendedMembership);

    createMembership(betaTenant, otherUser);

    List<AccessibleTenantProjection> projections =
        accessibleTenantQueryRepository.findAllByPlatformUserId(requestedUser.id());

    assertEquals(3, projections.size());

    assertProjection(
        projections.get(0),
        alphaMembership,
        alphaTenant,
        TenantStatus.ACTIVE,
        TenantMembershipStatus.ACTIVE);

    assertProjection(
        projections.get(1),
        betaMembership,
        betaTenant,
        TenantStatus.ACTIVE,
        TenantMembershipStatus.ACTIVE);

    assertProjection(
        projections.get(2),
        suspendedMembership,
        suspendedTenant,
        TenantStatus.SUSPENDED,
        TenantMembershipStatus.SUSPENDED);

    assertEquals(4L, springDataMembershipRepository.count());

    assertEquals(3L, springDataTenantRepository.count());

    assertEquals(2L, springDataPlatformUserRepository.count());
  }

  @Test
  void shouldReturnEmptyListWhenPlatformUserHasNoMemberships() {
    PlatformUser platformUser = createPlatformUser("user-without-memberships");

    List<AccessibleTenantProjection> projections =
        accessibleTenantQueryRepository.findAllByPlatformUserId(platformUser.id());

    assertEquals(List.of(), projections);
  }

  @Test
  void shouldRejectNullPlatformUserId() {
    NullPointerException exception =
        assertThrows(
            NullPointerException.class,
            () -> accessibleTenantQueryRepository.findAllByPlatformUserId(null));

    assertEquals("Platform user id cannot be null", exception.getMessage());
  }

  private Tenant createTenant(String key, String name) {
    return tenantRepository.save(Tenant.create(TenantKey.of(key), TenantName.of(name)));
  }

  private PlatformUser createPlatformUser(String subject) {
    return platformUserRepository.save(
        PlatformUser.create(ExternalIdentity.of("http://localhost:8180/realms/eoc", subject)));
  }

  private TenantMembership createMembership(Tenant tenant, PlatformUser platformUser) {
    return tenantMembershipRepository.save(TenantMembership.create(tenant.id(), platformUser.id()));
  }

  private static void assertProjection(
      AccessibleTenantProjection projection,
      TenantMembership membership,
      Tenant tenant,
      TenantStatus tenantStatus,
      TenantMembershipStatus membershipStatus) {
    assertEquals(membership.id(), projection.membershipId());

    assertEquals(tenant.id(), projection.tenantId());

    assertEquals(tenant.key(), projection.key());

    assertEquals(tenant.name(), projection.name());

    assertEquals(tenantStatus, projection.tenantStatus());

    assertEquals(membershipStatus, projection.membershipStatus());
  }
}
