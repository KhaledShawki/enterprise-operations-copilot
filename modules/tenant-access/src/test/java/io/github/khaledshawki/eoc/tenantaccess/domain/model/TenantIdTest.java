package io.github.khaledshawki.eoc.tenantaccess.domain.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;
import org.junit.jupiter.api.Test;

public class TenantIdTest {

  @Test
  void shouldGenerateTenantIdFromUUID() {
    UUID uuid = UUID.randomUUID();
    assertEquals(uuid, TenantId.of(uuid).value());
  }

  @Test
  void shouldGenerateTenantId() {
    assertNotNull(TenantId.generate().value());
  }

  @Test
  void shouldRejectNullUuid() {
    assertThrows(IllegalArgumentException.class, () -> TenantId.of(null));
  }
}
