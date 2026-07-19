package io.github.khaledshawki.eoc.tenantaccess.domain.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class PlatformUserIdTest {

  @Test
  void shouldCreatePlatformUserIdFromUuid() {
    UUID value = UUID.randomUUID();

    PlatformUserId userId = PlatformUserId.of(value);

    assertEquals(value, userId.value());
  }

  @Test
  void shouldGeneratePlatformUserId() {
    PlatformUserId userId = PlatformUserId.generate();

    assertNotNull(userId.value());
  }

  @Test
  void shouldRejectNullUuid() {
    assertThrows(NullPointerException.class, () -> PlatformUserId.of(null));
  }
}
