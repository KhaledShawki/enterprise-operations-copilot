package io.github.khaledshawki.eoc.platform.tenantaccess.adapter.out.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.khaledshawki.eoc.platform.TestcontainersConfiguration;
import io.github.khaledshawki.eoc.tenantaccess.application.exception.TenantMembershipAlreadyExistsException;
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
import io.github.khaledshawki.eoc.tenantaccess.domain.model.TenantMembershipStatus;
import io.github.khaledshawki.eoc.tenantaccess.domain.model.TenantName;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

@SpringBootTest
@Import({TestcontainersConfiguration.class, PersistenceTestClockConfiguration.class})
class TenantMembershipPersistenceAdapterIT {

  private static final Instant INITIAL_TIME = Instant.parse("2026-07-19T08:00:00Z");

  @Autowired private TenantMembershipRepository membershipRepository;
  @Autowired private TenantRepository tenantRepository;
  @Autowired private PlatformUserRepository platformUserRepository;

  @Autowired private SpringDataTenantMembershipRepository springDataMembershipRepository;
  @Autowired private SpringDataPlatformUserRepository springDataPlatformUserRepository;
  @Autowired private SpringDataTenantRepository springDataTenantRepository;

  @Autowired private MutableClock clock;

  @BeforeEach
  void setUp() {
    springDataMembershipRepository.deleteAllInBatch();
    springDataPlatformUserRepository.deleteAllInBatch();
    springDataTenantRepository.deleteAllInBatch();
    clock.setInstant(INITIAL_TIME);
  }

  @Test
  void shouldSaveAndFindTenantMembership() {
    Tenant tenant = createTenant();
    PlatformUser user = createPlatformUser("user-123");
    TenantMembership membership = TenantMembership.create(tenant.id(), user.id());

    TenantMembership savedMembership = membershipRepository.save(membership);

    assertEquals(membership.id(), savedMembership.id());
    assertEquals(tenant.id(), savedMembership.tenantId());
    assertEquals(user.id(), savedMembership.userId());
    assertEquals(TenantMembershipStatus.ACTIVE, savedMembership.status());

    TenantMembership loadedMembership =
        membershipRepository.findById(membership.id()).orElseThrow();

    assertEquals(savedMembership.id(), loadedMembership.id());
    assertEquals(savedMembership.tenantId(), loadedMembership.tenantId());
    assertEquals(savedMembership.userId(), loadedMembership.userId());
    assertEquals(savedMembership.status(), loadedMembership.status());

    assertTrue(membershipRepository.existsByTenantIdAndUserId(tenant.id(), user.id()));

    TenantMembership foundByTenantAndUser =
        membershipRepository.findByTenantIdAndUserId(tenant.id(), user.id()).orElseThrow();

    assertEquals(membership.id(), foundByTenantAndUser.id());

    TenantMembershipJpaEntity storedEntity =
        springDataMembershipRepository.findById(membership.id().value()).orElseThrow();

    assertEquals(membership.id().value(), storedEntity.getId());
    assertEquals(tenant.id().value(), storedEntity.getTenantId());
    assertEquals(user.id().value(), storedEntity.getPlatformUserId());
    assertEquals(TenantMembershipStatus.ACTIVE, storedEntity.getStatus());
    assertEquals(INITIAL_TIME, storedEntity.getCreatedAt());
    assertEquals(INITIAL_TIME, storedEntity.getUpdatedAt());
    assertEquals(0L, storedEntity.getVersion());
  }

  @Test
  void shouldUpdateTenantMembership() {
    Tenant tenant = createTenant();
    PlatformUser user = createPlatformUser("user-123");
    TenantMembership membership = TenantMembership.create(tenant.id(), user.id());
    membershipRepository.save(membership);

    Instant updatedTime = Instant.parse("2026-07-19T09:00:00Z");
    clock.setInstant(updatedTime);

    TenantMembership membershipToUpdate =
        membershipRepository.findById(membership.id()).orElseThrow();

    membershipToUpdate.suspend();

    TenantMembership updatedMembership = membershipRepository.save(membershipToUpdate);

    assertEquals(TenantMembershipStatus.SUSPENDED, updatedMembership.status());

    TenantMembershipJpaEntity updatedEntity =
        springDataMembershipRepository.findById(membership.id().value()).orElseThrow();

    assertEquals(membership.id().value(), updatedEntity.getId());
    assertEquals(tenant.id().value(), updatedEntity.getTenantId());
    assertEquals(user.id().value(), updatedEntity.getPlatformUserId());
    assertEquals(TenantMembershipStatus.SUSPENDED, updatedEntity.getStatus());
    assertEquals(INITIAL_TIME, updatedEntity.getCreatedAt());
    assertEquals(updatedTime, updatedEntity.getUpdatedAt());
    assertEquals(1L, updatedEntity.getVersion());
  }

  @Test
  void shouldRejectDuplicateTenantMembership() {
    Tenant tenant = createTenant();
    PlatformUser user = createPlatformUser("user-123");

    TenantMembership first = TenantMembership.create(tenant.id(), user.id());
    TenantMembership second = TenantMembership.create(tenant.id(), user.id());

    membershipRepository.save(first);

    TenantMembershipAlreadyExistsException exception =
        assertThrows(
            TenantMembershipAlreadyExistsException.class, () -> membershipRepository.save(second));

    assertInstanceOf(DataIntegrityViolationException.class, exception.getCause());
    assertEquals(1L, springDataMembershipRepository.count());
  }

  @Test
  void shouldRejectMembershipForUnknownTenant() {
    PlatformUser user = createPlatformUser("user-123");

    TenantMembership membership = TenantMembership.create(TenantId.generate(), user.id());

    assertThrows(
        DataIntegrityViolationException.class, () -> membershipRepository.save(membership));
  }

  @Test
  void shouldRejectMembershipForUnknownPlatformUser() {
    Tenant tenant = createTenant();

    TenantMembership membership = TenantMembership.create(tenant.id(), PlatformUserId.generate());

    assertThrows(
        DataIntegrityViolationException.class, () -> membershipRepository.save(membership));
  }

  private Tenant createTenant() {
    return tenantRepository.save(
        Tenant.create(TenantKey.of("tenant-key"), TenantName.of("Tenant Name")));
  }

  private PlatformUser createPlatformUser(String subject) {
    return platformUserRepository.save(
        PlatformUser.create(ExternalIdentity.of("http://localhost:8180/realms/eoc", subject)));
  }
}
