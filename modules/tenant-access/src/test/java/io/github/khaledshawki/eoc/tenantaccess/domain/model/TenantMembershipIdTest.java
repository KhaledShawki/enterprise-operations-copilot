package io.github.khaledshawki.eoc.tenantaccess.domain.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class TenantMembershipIdTest {

  @Test
  void shouldCreateTenantMembershipIdFromUuid() {
    UUID value = UUID.randomUUID();

    TenantMembershipId membershipId = TenantMembershipId.of(value);

    assertEquals(value, membershipId.value());
  }

  @Test
  void shouldGenerateTenantMembershipId() {
    TenantMembershipId membershipId = TenantMembershipId.generate();

    assertNotNull(membershipId.value());
  }

  @Test
  void shouldRejectNullUuid() {
    assertThrows(NullPointerException.class, () -> TenantMembershipId.of(null));
  }
}
