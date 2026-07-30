package io.github.khaledshawki.eoc.platform.tenantaccess.adapter.out.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.khaledshawki.eoc.platform.TestcontainersConfiguration;
import io.github.khaledshawki.eoc.tenantaccess.application.port.out.PlatformUserRepository;
import io.github.khaledshawki.eoc.tenantaccess.application.port.out.TenantMembershipRepository;
import io.github.khaledshawki.eoc.tenantaccess.application.port.out.TenantMembershipRoleWriteRepository;
import io.github.khaledshawki.eoc.tenantaccess.application.port.out.TenantRepository;
import io.github.khaledshawki.eoc.tenantaccess.domain.model.ExternalIdentity;
import io.github.khaledshawki.eoc.tenantaccess.domain.model.PlatformUser;
import io.github.khaledshawki.eoc.tenantaccess.domain.model.Tenant;
import io.github.khaledshawki.eoc.tenantaccess.domain.model.TenantKey;
import io.github.khaledshawki.eoc.tenantaccess.domain.model.TenantMembership;
import io.github.khaledshawki.eoc.tenantaccess.domain.model.TenantMembershipId;
import io.github.khaledshawki.eoc.tenantaccess.domain.model.TenantMembershipStatus;
import io.github.khaledshawki.eoc.tenantaccess.domain.model.TenantName;
import io.github.khaledshawki.eoc.tenantaccess.domain.model.TenantRoleKey;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
@Import({TestcontainersConfiguration.class, PersistenceTestClockConfiguration.class})
class TenantMembershipRolePersistenceIT {

  private static final Instant INITIAL_TIME = Instant.parse("2026-07-29T08:00:00Z");

  @Autowired private TenantMembershipRepository membershipRepository;

  @Autowired private TenantMembershipRoleWriteRepository membershipRoleWriteRepository;

  @Autowired private TenantRepository tenantRepository;

  @Autowired private PlatformUserRepository platformUserRepository;

  @Autowired private SpringDataTenantMembershipRepository springDataMembershipRepository;

  @Autowired private SpringDataPlatformUserRepository springDataPlatformUserRepository;

  @Autowired private SpringDataTenantRepository springDataTenantRepository;

  @Autowired private JdbcTemplate jdbcTemplate;

  @Autowired private MutableClock clock;

  @BeforeEach
  void setUp() {
    springDataMembershipRepository.deleteAllInBatch();
    springDataPlatformUserRepository.deleteAllInBatch();
    springDataTenantRepository.deleteAllInBatch();

    clock.setInstant(INITIAL_TIME);
  }

  @Test
  void shouldPersistAndReloadTenantMembershipRoles() {
    Tenant tenant = createTenant("tenant-one");

    PlatformUser user = createPlatformUser("user-one");

    Set<TenantRoleKey> roles =
        Set.of(TenantRoleKey.of("tenant-admin"), TenantRoleKey.of("auditor"));

    TenantMembership membership =
        TenantMembership.reconstitute(
            TenantMembershipId.generate(),
            tenant.id(),
            user.id(),
            TenantMembershipStatus.ACTIVE,
            roles);

    TenantMembership savedMembership = membershipRepository.save(membership);

    assertEquals(roles, savedMembership.roles());

    TenantMembership loadedMembership =
        membershipRepository.findById(membership.id()).orElseThrow();

    assertEquals(roles, loadedMembership.roles());

    assertEquals(Set.of("tenant-admin", "auditor"), storedRoleKeys(membership.id().value()));
  }

  @Test
  void shouldReplacePersistedTenantMembershipRoles() {
    Tenant tenant = createTenant("tenant-one");

    PlatformUser user = createPlatformUser("user-one");

    TenantMembership membership =
        TenantMembership.reconstitute(
            TenantMembershipId.generate(),
            tenant.id(),
            user.id(),
            TenantMembershipStatus.ACTIVE,
            Set.of(TenantRoleKey.of("tenant-admin"), TenantRoleKey.of("auditor")));

    membershipRepository.save(membership);

    long initialVersion = storedMembershipVersion(membership.id().value());

    Instant updatedTime = Instant.parse("2026-07-29T09:00:00Z");

    clock.setInstant(updatedTime);

    TenantMembership membershipToUpdate =
        membershipRepository.findById(membership.id()).orElseThrow();

    membershipToUpdate.replaceRoles(Set.of(TenantRoleKey.of("operations-manager")));

    TenantMembership updatedMembership =
        membershipRoleWriteRepository.replaceRoles(membershipToUpdate);

    assertEquals(Set.of(TenantRoleKey.of("operations-manager")), updatedMembership.roles());

    assertEquals(TenantMembershipStatus.ACTIVE, updatedMembership.status());

    assertEquals(Set.of("operations-manager"), storedRoleKeys(membership.id().value()));

    assertEquals(initialVersion + 1L, storedMembershipVersion(membership.id().value()));

    assertEquals(updatedTime, storedMembershipUpdatedAt(membership.id().value()));
  }

  @Test
  void shouldClearPersistedTenantMembershipRoles() {
    Tenant tenant = createTenant("tenant-one");

    PlatformUser user = createPlatformUser("user-one");

    TenantMembership membership =
        TenantMembership.reconstitute(
            TenantMembershipId.generate(),
            tenant.id(),
            user.id(),
            TenantMembershipStatus.ACTIVE,
            Set.of(TenantRoleKey.of("tenant-admin"), TenantRoleKey.of("auditor")));

    membershipRepository.save(membership);

    TenantMembership membershipToUpdate =
        membershipRepository.findById(membership.id()).orElseThrow();

    membershipToUpdate.replaceRoles(Set.of());

    TenantMembership updatedMembership =
        membershipRoleWriteRepository.replaceRoles(membershipToUpdate);

    assertEquals(Set.of(), updatedMembership.roles());

    assertEquals(TenantMembershipStatus.ACTIVE, updatedMembership.status());

    assertEquals(Set.of(), storedRoleKeys(membership.id().value()));
  }

  @Test
  void shouldRejectInvalidRoleKeyAtDatabaseBoundary() {
    Tenant tenant = createTenant("tenant-one");

    PlatformUser user = createPlatformUser("user-one");

    TenantMembership membership =
        membershipRepository.save(TenantMembership.create(tenant.id(), user.id()));

    assertThrows(
        DataIntegrityViolationException.class,
        () ->
            jdbcTemplate.update(
                """
                INSERT INTO tenant_membership_roles (
                  tenant_membership_id,
                  role_key
                )
                VALUES (?, ?)
                """,
                membership.id().value(),
                "invalid_role"));
  }

  @Test
  void shouldCascadeDeleteRolesWhenMembershipIsDeleted() {
    Tenant tenant = createTenant("tenant-one");

    PlatformUser user = createPlatformUser("user-one");

    TenantMembership membership =
        TenantMembership.reconstitute(
            TenantMembershipId.generate(),
            tenant.id(),
            user.id(),
            TenantMembershipStatus.ACTIVE,
            Set.of(TenantRoleKey.of("tenant-admin")));

    membershipRepository.save(membership);

    assertEquals(1L, storedRoleCount(membership.id().value()));

    assertEquals(
        1,
        jdbcTemplate.update(
            """
            DELETE FROM tenant_memberships
            WHERE id = ?
            """,
            membership.id().value()));

    assertEquals(0L, storedRoleCount(membership.id().value()));
  }

  @Test
  void shouldPreserveRoleReplacementWhenStaleStatusSnapshotIsSaved() {
    Tenant tenant = createTenant("tenant-one");

    PlatformUser user = createPlatformUser("user-one");

    TenantMembership membership =
        TenantMembership.reconstitute(
            TenantMembershipId.generate(),
            tenant.id(),
            user.id(),
            TenantMembershipStatus.ACTIVE,
            Set.of(TenantRoleKey.of("auditor")));

    membershipRepository.save(membership);

    TenantMembership roleSnapshot = membershipRepository.findById(membership.id()).orElseThrow();

    TenantMembership statusSnapshot = membershipRepository.findById(membership.id()).orElseThrow();

    roleSnapshot.replaceRoles(Set.of(TenantRoleKey.of("operations-manager")));

    membershipRoleWriteRepository.replaceRoles(roleSnapshot);

    statusSnapshot.suspend();

    membershipRepository.save(statusSnapshot);

    TenantMembership reloadedMembership =
        membershipRepository.findById(membership.id()).orElseThrow();

    assertEquals(TenantMembershipStatus.SUSPENDED, reloadedMembership.status());

    assertEquals(Set.of(TenantRoleKey.of("operations-manager")), reloadedMembership.roles());

    assertEquals(Set.of("operations-manager"), storedRoleKeys(membership.id().value()));
  }

  @Test
  void shouldPreserveSuspensionWhenStaleRoleSnapshotIsSaved() {
    Tenant tenant = createTenant("tenant-one");

    PlatformUser user = createPlatformUser("user-one");

    TenantMembership membership =
        TenantMembership.reconstitute(
            TenantMembershipId.generate(),
            tenant.id(),
            user.id(),
            TenantMembershipStatus.ACTIVE,
            Set.of(TenantRoleKey.of("auditor")));

    membershipRepository.save(membership);

    TenantMembership roleSnapshot = membershipRepository.findById(membership.id()).orElseThrow();

    TenantMembership statusSnapshot = membershipRepository.findById(membership.id()).orElseThrow();

    statusSnapshot.suspend();

    membershipRepository.save(statusSnapshot);

    roleSnapshot.replaceRoles(Set.of(TenantRoleKey.of("operations-manager")));

    membershipRoleWriteRepository.replaceRoles(roleSnapshot);

    TenantMembership reloadedMembership =
        membershipRepository.findById(membership.id()).orElseThrow();

    assertEquals(TenantMembershipStatus.SUSPENDED, reloadedMembership.status());

    assertEquals(Set.of(TenantRoleKey.of("operations-manager")), reloadedMembership.roles());

    assertEquals(Set.of("operations-manager"), storedRoleKeys(membership.id().value()));
  }

  private Tenant createTenant(String key) {
    return tenantRepository.save(Tenant.create(TenantKey.of(key), TenantName.of("Tenant " + key)));
  }

  private PlatformUser createPlatformUser(String subject) {
    return platformUserRepository.save(
        PlatformUser.create(ExternalIdentity.of("http://localhost:8180/realms/eoc", subject)));
  }

  private Set<String> storedRoleKeys(UUID membershipId) {
    return new LinkedHashSet<>(
        jdbcTemplate.queryForList(
            """
            SELECT role_key
            FROM tenant_membership_roles
            WHERE tenant_membership_id = ?
            ORDER BY role_key
            """,
            String.class,
            membershipId));
  }

  private long storedRoleCount(UUID membershipId) {
    return jdbcTemplate.queryForObject(
        """
        SELECT COUNT(*)
        FROM tenant_membership_roles
        WHERE tenant_membership_id = ?
        """,
        Long.class,
        membershipId);
  }

  private long storedMembershipVersion(UUID membershipId) {
    return jdbcTemplate.queryForObject(
        """
        SELECT version
        FROM tenant_memberships
        WHERE id = ?
        """,
        Long.class,
        membershipId);
  }

  private Instant storedMembershipUpdatedAt(UUID membershipId) {
    return jdbcTemplate.queryForObject(
        """
        SELECT updated_at
        FROM tenant_memberships
        WHERE id = ?
        """,
        Instant.class,
        membershipId);
  }
}
