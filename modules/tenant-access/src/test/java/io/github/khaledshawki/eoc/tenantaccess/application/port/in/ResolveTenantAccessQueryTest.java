package io.github.khaledshawki.eoc.tenantaccess.application.port.in;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class ResolveTenantAccessQueryTest {

  private static final UUID TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

  @Test
  void shouldPreserveValidatedValues() {
    ResolveTenantAccessQuery query =
        new ResolveTenantAccessQuery("issuer", "subject", TENANT_ID, "auditor");

    assertEquals("issuer", query.issuer());
    assertEquals("subject", query.subject());
    assertEquals(TENANT_ID, query.tenantId());
    assertEquals("auditor", query.requiredRole());
  }

  @Test
  void shouldRejectInvalidValues() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new ResolveTenantAccessQuery(" ", "subject", TENANT_ID, "auditor"));
    assertThrows(
        IllegalArgumentException.class,
        () -> new ResolveTenantAccessQuery("issuer", " ", TENANT_ID, "auditor"));
    assertThrows(
        NullPointerException.class,
        () -> new ResolveTenantAccessQuery("issuer", "subject", null, "auditor"));
    assertThrows(
        IllegalArgumentException.class,
        () -> new ResolveTenantAccessQuery("issuer", "subject", TENANT_ID, " "));
  }
}
