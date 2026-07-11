package io.github.khaledshawki.eoc.tenantaccess.domain.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

public class TenantTest {

  @Test
  void shouldCreateActiveTenantWithGeneratedId() {
    Tenant tenant = Tenant.create(TenantName.of("Tenant Name"));

    assertNotNull(tenant.id());
    assertEquals(TenantName.of("Tenant Name"), tenant.name());
    assertEquals(TenantStatus.ACTIVE, tenant.status());
  }

  @Test
  void shouldReconstituteTenant() {
    TenantId tenantId = TenantId.generate();
    TenantName tenantName = TenantName.of("Tenant Name");

    Tenant tenant = Tenant.reconstitute(tenantId, tenantName, TenantStatus.SUSPENDED);

    assertEquals(tenantId, tenant.id());
    assertEquals(tenantName, tenant.name());
    assertEquals(TenantStatus.SUSPENDED, tenant.status());
  }

  @Test
  void shouldRenameTenant() {
    Tenant tenant = Tenant.create(TenantName.of("Tenant Name"));
    tenant.rename(TenantName.of("New Tenant Name"));

    assertEquals(TenantName.of("New Tenant Name"), tenant.name());
  }

  @Test
  void shouldSuspendTenant() {
    Tenant tenant = Tenant.create(TenantName.of("Tenant Name"));
    tenant.suspend();

    assertEquals(TenantStatus.SUSPENDED, tenant.status());
  }

  @Test
  void shouldActivateTenant() {
    Tenant tenant =
        Tenant.reconstitute(
            TenantId.generate(), TenantName.of("Tenant Name"), TenantStatus.SUSPENDED);
    tenant.activate();

    assertEquals(TenantStatus.ACTIVE, tenant.status());
  }

  @Test
  void shouldRejectSupendingAlreadySuspendedTenant() {
    Tenant tenant = Tenant.create(TenantName.of("Tenant Name"));
    tenant.suspend();

    assertThrows(IllegalStateException.class, tenant::suspend);
  }

  @Test
  void shouldRejectActivatingAlreadyActiveTenant() {
    Tenant tenant = Tenant.create(TenantName.of("Tenant Name"));
    assertThrows(IllegalStateException.class, tenant::activate);
  }

  @Test
  void shouldRejectNullName() {
    assertThrows(IllegalArgumentException.class, () -> Tenant.create(null));
  }

  @Test
  void shouldRejectRenamedTenantWithNullName() {
    Tenant tenant = Tenant.create(TenantName.of("Tenant Name"));
    assertThrows(IllegalArgumentException.class, () -> tenant.rename(null));
  }

  @Test
  void shouldRejectMissingStateWhenReconstitutingTenant() {
    TenantId tenantId = TenantId.generate();
    TenantName tenantName = TenantName.of("Tenant Name");

    assertThrows(
        IllegalArgumentException.class,
        () -> Tenant.reconstitute(null, tenantName, TenantStatus.ACTIVE));
    assertThrows(
        IllegalArgumentException.class,
        () -> Tenant.reconstitute(tenantId, null, TenantStatus.ACTIVE));
    assertThrows(
        IllegalArgumentException.class, () -> Tenant.reconstitute(tenantId, tenantName, null));
  }
}
