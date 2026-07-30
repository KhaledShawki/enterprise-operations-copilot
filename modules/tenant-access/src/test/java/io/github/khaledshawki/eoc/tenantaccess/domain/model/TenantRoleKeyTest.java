package io.github.khaledshawki.eoc.tenantaccess.domain.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class TenantRoleKeyTest {

  @Test
  void shouldCreateValidTenantRoleKey() {
    TenantRoleKey roleKey = TenantRoleKey.of("tenant-admin");

    assertEquals("tenant-admin", roleKey.value());
  }

  @Test
  void shouldNormalizeTenantRoleKey() {
    TenantRoleKey roleKey = TenantRoleKey.of("  Operations-Manager  ");

    assertEquals("operations-manager", roleKey.value());
  }

  @Test
  void shouldAcceptTenantRoleKeyAtMaximumLength() {
    String value = "a".repeat(TenantRoleKey.MAX_LENGTH);

    TenantRoleKey roleKey = TenantRoleKey.of(value);

    assertEquals(value, roleKey.value());
  }

  @Test
  void shouldRejectTenantRoleKeyLongerThanMaximumLength() {
    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () -> TenantRoleKey.of("a".repeat(TenantRoleKey.MAX_LENGTH + 1)));

    assertEquals(
        "Tenant role key cannot be longer than " + TenantRoleKey.MAX_LENGTH + " characters",
        exception.getMessage());
  }

  @Test
  void shouldRejectNullTenantRoleKey() {
    NullPointerException exception =
        assertThrows(NullPointerException.class, () -> TenantRoleKey.of(null));

    assertEquals("Tenant role key cannot be null", exception.getMessage());
  }

  @Test
  void shouldRejectEmptyTenantRoleKey() {
    IllegalArgumentException exception =
        assertThrows(IllegalArgumentException.class, () -> TenantRoleKey.of(""));

    assertEquals("Tenant role key cannot be empty", exception.getMessage());
  }

  @Test
  void shouldRejectBlankTenantRoleKey() {
    IllegalArgumentException exception =
        assertThrows(IllegalArgumentException.class, () -> TenantRoleKey.of("   "));

    assertEquals("Tenant role key cannot be empty", exception.getMessage());
  }

  @Test
  void shouldRejectTenantRoleKeyWithInvalidFormat() {
    assertInvalidFormat("-tenant-admin");

    assertInvalidFormat("tenant-admin-");

    assertInvalidFormat("tenant--admin");

    assertInvalidFormat("tenant_admin");

    assertInvalidFormat("tenant admin");

    assertInvalidFormat("tenant.admin");
  }

  private static void assertInvalidFormat(String value) {
    IllegalArgumentException exception =
        assertThrows(IllegalArgumentException.class, () -> TenantRoleKey.of(value));

    assertEquals("Tenant role key has an invalid format", exception.getMessage());
  }
}
