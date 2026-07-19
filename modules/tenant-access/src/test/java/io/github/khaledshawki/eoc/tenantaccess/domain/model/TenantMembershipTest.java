package io.github.khaledshawki.eoc.tenantaccess.domain.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
