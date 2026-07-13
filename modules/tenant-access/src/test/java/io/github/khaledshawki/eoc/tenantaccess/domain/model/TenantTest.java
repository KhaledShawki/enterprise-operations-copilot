package io.github.khaledshawki.eoc.tenantaccess.domain.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class TenantTest {

  private static final TenantKey TENANT_KEY = TenantKey.of("tenant-key");

  private static final TenantName TENANT_NAME = TenantName.of("Tenant Name");

  @Test
  void shouldCreateActiveTenantWithGeneratedId() {
    Tenant tenant = Tenant.create(TENANT_KEY, TENANT_NAME);

    assertNotNull(tenant.id());
    assertEquals(TENANT_KEY, tenant.key());
    assertEquals(TENANT_NAME, tenant.name());
    assertEquals(TenantStatus.ACTIVE, tenant.status());
  }

  @Test
  void shouldReconstituteExistingTenant() {
    TenantId tenantId = TenantId.generate();

    Tenant tenant = Tenant.reconstitute(tenantId, TENANT_KEY, TENANT_NAME, TenantStatus.SUSPENDED);

    assertEquals(tenantId, tenant.id());
    assertEquals(TENANT_KEY, tenant.key());
    assertEquals(TENANT_NAME, tenant.name());
    assertEquals(TenantStatus.SUSPENDED, tenant.status());
  }

  @Test
  void shouldRenameTenant() {
    Tenant tenant = Tenant.create(TENANT_KEY, TENANT_NAME);
    TenantName newName = TenantName.of("New Tenant Name");

    tenant.rename(newName);

    assertEquals(newName, tenant.name());
  }

  @Test
  void shouldSuspendActiveTenant() {
    Tenant tenant = Tenant.create(TENANT_KEY, TENANT_NAME);

    tenant.suspend();

    assertEquals(TenantStatus.SUSPENDED, tenant.status());
  }

  @Test
  void shouldActivateSuspendedTenant() {
    Tenant tenant =
        Tenant.reconstitute(TenantId.generate(), TENANT_KEY, TENANT_NAME, TenantStatus.SUSPENDED);

    tenant.activate();

    assertEquals(TenantStatus.ACTIVE, tenant.status());
  }

  @Test
  void shouldRejectSuspendingAlreadySuspendedTenant() {
    Tenant tenant =
        Tenant.reconstitute(TenantId.generate(), TENANT_KEY, TENANT_NAME, TenantStatus.SUSPENDED);

    IllegalStateException exception = assertThrows(IllegalStateException.class, tenant::suspend);

    assertEquals("Tenant is already suspended", exception.getMessage());
  }

  @Test
  void shouldRejectActivatingAlreadyActiveTenant() {
    Tenant tenant = Tenant.create(TENANT_KEY, TENANT_NAME);

    IllegalStateException exception = assertThrows(IllegalStateException.class, tenant::activate);

    assertEquals("Tenant is already active", exception.getMessage());
  }

  @Test
  void shouldRejectNullKeyWhenCreatingTenant() {
    assertThrows(NullPointerException.class, () -> Tenant.create(null, TENANT_NAME));
  }

  @Test
  void shouldRejectNullNameWhenCreatingTenant() {
    assertThrows(NullPointerException.class, () -> Tenant.create(TENANT_KEY, null));
  }

  @Test
  void shouldRejectNullNameWhenRenamingTenant() {
    Tenant tenant = Tenant.create(TENANT_KEY, TENANT_NAME);

    assertThrows(NullPointerException.class, () -> tenant.rename(null));
  }

  @Test
  void shouldRejectNullIdWhenReconstitutingTenant() {
    assertThrows(
        NullPointerException.class,
        () -> Tenant.reconstitute(null, TENANT_KEY, TENANT_NAME, TenantStatus.ACTIVE));
  }

  @Test
  void shouldRejectNullKeyWhenReconstitutingTenant() {
    assertThrows(
        NullPointerException.class,
        () -> Tenant.reconstitute(TenantId.generate(), null, TENANT_NAME, TenantStatus.ACTIVE));
  }

  @Test
  void shouldRejectNullNameWhenReconstitutingTenant() {
    assertThrows(
        NullPointerException.class,
        () -> Tenant.reconstitute(TenantId.generate(), TENANT_KEY, null, TenantStatus.ACTIVE));
  }

  @Test
  void shouldRejectNullStatusWhenReconstitutingTenant() {
    assertThrows(
        NullPointerException.class,
        () -> Tenant.reconstitute(TenantId.generate(), TENANT_KEY, TENANT_NAME, null));
  }
}
