package io.github.khaledshawki.eoc.tenantaccess.domain.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class TenantNameTest {

  @Test
  void shouldCreateValidTenantName() {
    TenantName tenantName = new TenantName("Tenant Name");
    assertEquals("Tenant Name", tenantName.value());
  }

  @Test
  void shouldTrimTenantName() {
    TenantName tenantName = new TenantName("  Tenant Name  ");
    assertEquals("Tenant Name", tenantName.value());
  }

  @Test
  void shouldRejectEmptyTenantName() {
    assertThrows(IllegalArgumentException.class, () -> TenantName.of(""));
  }

  @Test
  void shouldAcceptTenantNameWithMaxLength() {
    TenantName tenantName = new TenantName("a".repeat(TenantName.MAX_LENGTH));
    assertEquals(tenantName.value(), "a".repeat(TenantName.MAX_LENGTH));
  }

  @Test
  void shouldRejectTenantNameLongerThanMaxLength() {
    assertThrows(
        IllegalArgumentException.class, () -> TenantName.of("a".repeat(TenantName.MAX_LENGTH + 1)));
  }

  @Test
  void shouldRejectNullTenantName() {
    assertThrows(NullPointerException.class, () -> TenantName.of(null));
  }
}
