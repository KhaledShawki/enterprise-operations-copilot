package io.github.khaledshawki.eoc.tenantaccess.domain.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class TenantMembershipTest {

  private static final TenantId TENANT_ID = TenantId.generate();
  private static final PlatformUserId USER_ID = PlatformUserId.generate();

  @Test
  void shouldCreateActiveTenantMembershipWithGeneratedId() {
    TenantMembership membership = TenantMembership.create(TENANT_ID, USER_ID);

    assertNotNull(membership.id());
    assertEquals(TENANT_ID, membership.tenantId());
    assertEquals(USER_ID, membership.userId());
    assertEquals(TenantMembershipStatus.ACTIVE, membership.status());

    assertEquals(Set.of(), membership.roles());
  }

  @Test
  void shouldReconstituteExistingTenantMembership() {
    TenantMembershipId membershipId = TenantMembershipId.generate();

    TenantMembership membership =
        TenantMembership.reconstitute(
            membershipId, TENANT_ID, USER_ID, TenantMembershipStatus.SUSPENDED);

    assertEquals(membershipId, membership.id());
    assertEquals(TENANT_ID, membership.tenantId());
    assertEquals(USER_ID, membership.userId());
    assertEquals(TenantMembershipStatus.SUSPENDED, membership.status());
  }

  @Test
  void shouldReconstituteExistingTenantMembershipWithRoles() {
    TenantMembershipId membershipId = TenantMembershipId.generate();

    Set<TenantRoleKey> roles =
        Set.of(TenantRoleKey.of("tenant-admin"), TenantRoleKey.of("auditor"));

    TenantMembership membership =
        TenantMembership.reconstitute(
            membershipId, TENANT_ID, USER_ID, TenantMembershipStatus.ACTIVE, roles);

    assertEquals(membershipId, membership.id());
    assertEquals(TENANT_ID, membership.tenantId());
    assertEquals(USER_ID, membership.userId());
    assertEquals(TenantMembershipStatus.ACTIVE, membership.status());
    assertEquals(roles, membership.roles());
  }

  @Test
  void shouldReplaceTenantMembershipRoles() {
    TenantMembership membership = TenantMembership.create(TENANT_ID, USER_ID);

    Set<TenantRoleKey> roles =
        Set.of(TenantRoleKey.of("tenant-admin"), TenantRoleKey.of("operations-manager"));

    membership.replaceRoles(roles);

    assertEquals(roles, membership.roles());
  }

  @Test
  void shouldAllowClearingTenantMembershipRoles() {
    TenantMembership membership =
        TenantMembership.reconstitute(
            TenantMembershipId.generate(),
            TENANT_ID,
            USER_ID,
            TenantMembershipStatus.ACTIVE,
            Set.of(TenantRoleKey.of("tenant-admin")));

    membership.replaceRoles(Set.of());

    assertEquals(Set.of(), membership.roles());
  }

  @Test
  void shouldDefensivelyCopyRolesWhenReconstitutingMembership() {
    Set<TenantRoleKey> roles = new HashSet<>();
    roles.add(TenantRoleKey.of("tenant-admin"));

    TenantMembership membership =
        TenantMembership.reconstitute(
            TenantMembershipId.generate(),
            TENANT_ID,
            USER_ID,
            TenantMembershipStatus.ACTIVE,
            roles);

    roles.add(TenantRoleKey.of("auditor"));

    assertEquals(Set.of(TenantRoleKey.of("tenant-admin")), membership.roles());
  }

  @Test
  void shouldDefensivelyCopyRolesWhenReplacingMembershipRoles() {
    TenantMembership membership = TenantMembership.create(TENANT_ID, USER_ID);

    Set<TenantRoleKey> roles = new HashSet<>();
    roles.add(TenantRoleKey.of("tenant-admin"));

    membership.replaceRoles(roles);

    roles.add(TenantRoleKey.of("auditor"));

    assertEquals(Set.of(TenantRoleKey.of("tenant-admin")), membership.roles());
  }

  @Test
  void shouldExposeUnmodifiableTenantMembershipRoles() {
    TenantMembership membership =
        TenantMembership.reconstitute(
            TenantMembershipId.generate(),
            TENANT_ID,
            USER_ID,
            TenantMembershipStatus.ACTIVE,
            Set.of(TenantRoleKey.of("tenant-admin")));

    assertThrows(
        UnsupportedOperationException.class,
        () -> membership.roles().add(TenantRoleKey.of("auditor")));
  }

  @Test
  void shouldPreserveTenantMembershipRolesAcrossStatusTransitions() {
    Set<TenantRoleKey> roles =
        Set.of(TenantRoleKey.of("tenant-admin"), TenantRoleKey.of("auditor"));

    TenantMembership membership =
        TenantMembership.reconstitute(
            TenantMembershipId.generate(),
            TENANT_ID,
            USER_ID,
            TenantMembershipStatus.ACTIVE,
            roles);

    membership.suspend();

    assertEquals(roles, membership.roles());

    membership.activate();

    assertEquals(roles, membership.roles());
  }

  @Test
  void shouldRejectNullRolesWhenReconstitutingMembership() {
    NullPointerException exception =
        assertThrows(
            NullPointerException.class,
            () ->
                TenantMembership.reconstitute(
                    TenantMembershipId.generate(),
                    TENANT_ID,
                    USER_ID,
                    TenantMembershipStatus.ACTIVE,
                    null));

    assertEquals("Tenant membership roles cannot be null", exception.getMessage());
  }

  @Test
  void shouldRejectNullRolesWhenReplacingMembershipRoles() {
    TenantMembership membership = TenantMembership.create(TENANT_ID, USER_ID);

    NullPointerException exception =
        assertThrows(NullPointerException.class, () -> membership.replaceRoles(null));

    assertEquals("Tenant membership roles cannot be null", exception.getMessage());
  }

  @Test
  void shouldRejectNullRoleWhenReconstitutingMembership() {
    Set<TenantRoleKey> roles = new HashSet<>();
    roles.add(null);

    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                TenantMembership.reconstitute(
                    TenantMembershipId.generate(),
                    TENANT_ID,
                    USER_ID,
                    TenantMembershipStatus.ACTIVE,
                    roles));

    assertEquals("Tenant membership roles cannot contain null values", exception.getMessage());
  }

  @Test
  void shouldRejectNullRoleWhenReplacingMembershipRoles() {
    TenantMembership membership = TenantMembership.create(TENANT_ID, USER_ID);

    Set<TenantRoleKey> roles = new HashSet<>();
    roles.add(null);

    IllegalArgumentException exception =
        assertThrows(IllegalArgumentException.class, () -> membership.replaceRoles(roles));

    assertEquals("Tenant membership roles cannot contain null values", exception.getMessage());
  }

  @Test
  void shouldSuspendActiveTenantMembership() {
    TenantMembership membership = TenantMembership.create(TENANT_ID, USER_ID);

    membership.suspend();

    assertEquals(TenantMembershipStatus.SUSPENDED, membership.status());
  }

  @Test
  void shouldActivateSuspendedTenantMembership() {
    TenantMembership membership =
        TenantMembership.reconstitute(
            TenantMembershipId.generate(), TENANT_ID, USER_ID, TenantMembershipStatus.SUSPENDED);

    membership.activate();

    assertEquals(TenantMembershipStatus.ACTIVE, membership.status());
  }

  @Test
  void shouldRejectSuspendingAlreadySuspendedTenantMembership() {
    TenantMembership membership =
        TenantMembership.reconstitute(
            TenantMembershipId.generate(), TENANT_ID, USER_ID, TenantMembershipStatus.SUSPENDED);

    IllegalStateException exception =
        assertThrows(IllegalStateException.class, membership::suspend);

    assertEquals("Tenant membership is already suspended", exception.getMessage());
  }

  @Test
  void shouldRejectActivatingAlreadyActiveTenantMembership() {
    TenantMembership membership = TenantMembership.create(TENANT_ID, USER_ID);

    IllegalStateException exception =
        assertThrows(IllegalStateException.class, membership::activate);

    assertEquals("Tenant membership is already active", exception.getMessage());
  }

  @Test
  void shouldRejectNullTenantWhenCreatingMembership() {
    assertThrows(NullPointerException.class, () -> TenantMembership.create(null, USER_ID));
  }

  @Test
  void shouldRejectNullUserWhenCreatingMembership() {
    assertThrows(NullPointerException.class, () -> TenantMembership.create(TENANT_ID, null));
  }

  @Test
  void shouldRejectNullIdWhenReconstitutingMembership() {
    assertThrows(
        NullPointerException.class,
        () ->
            TenantMembership.reconstitute(null, TENANT_ID, USER_ID, TenantMembershipStatus.ACTIVE));
  }

  @Test
  void shouldRejectNullTenantWhenReconstitutingMembership() {
    assertThrows(
        NullPointerException.class,
        () ->
            TenantMembership.reconstitute(
                TenantMembershipId.generate(), null, USER_ID, TenantMembershipStatus.ACTIVE));
  }

  @Test
  void shouldRejectNullUserWhenReconstitutingMembership() {
    assertThrows(
        NullPointerException.class,
        () ->
            TenantMembership.reconstitute(
                TenantMembershipId.generate(), TENANT_ID, null, TenantMembershipStatus.ACTIVE));
  }

  @Test
  void shouldRejectNullStatusWhenReconstitutingMembership() {
    assertThrows(
        NullPointerException.class,
        () ->
            TenantMembership.reconstitute(TenantMembershipId.generate(), TENANT_ID, USER_ID, null));
  }
}
