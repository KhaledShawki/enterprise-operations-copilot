package io.github.khaledshawki.eoc.tenantaccess.domain.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class TenantKeyTest {

  @Test
  void shouldCreateValidTenantKey() {
    TenantKey tenantKey = new TenantKey("tenant-key");
    assertEquals("tenant-key", tenantKey.value());
  }

  @Test
  void shouldTrimTenantKey() {
    TenantKey tenantKey = new TenantKey("  tenant-key  ");
    assertEquals("tenant-key", tenantKey.value());
  }

  @Test
  void shouldAcceptTenantKeyWithMaxLength() {
    TenantKey tenantKey = new TenantKey("a".repeat(TenantKey.MAX_LENGTH));
    assertEquals(tenantKey.value(), "a".repeat(TenantKey.MAX_LENGTH));
  }

  @Test
  void shouldRejectTenantKeyLongerThanMaxValue() {
    assertThrows(
        IllegalArgumentException.class, () -> TenantKey.of("a".repeat(TenantKey.MAX_LENGTH + 1)));
  }

  @Test
  void shouldRejectNullTenantKey() {
    assertThrows(NullPointerException.class, () -> TenantKey.of(null));
  }

  @Test
  void shouldRejectEmptyOrBlankTenantKey() {
    assertThrows(IllegalArgumentException.class, () -> TenantKey.of(""));
    assertThrows(IllegalArgumentException.class, () -> TenantKey.of("   "));
  }

  @Test
  void shouldRejectTenantKeyWithInvalidCharacters() {
    assertThrows(IllegalArgumentException.class, () -> TenantKey.of("-tenant"));
    assertThrows(IllegalArgumentException.class, () -> TenantKey.of("tenant-"));
    assertThrows(IllegalArgumentException.class, () -> TenantKey.of("tenant--key"));
    assertThrows(IllegalArgumentException.class, () -> TenantKey.of("tenant_key"));
    assertThrows(IllegalArgumentException.class, () -> TenantKey.of("tenant key"));
    assertThrows(IllegalArgumentException.class, () -> TenantKey.of("tenant.key"));
  }
}
