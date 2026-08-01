package io.github.khaledshawki.eoc.connectormanagement.domain.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class ConnectorTenantIdTest {

  @Test
  void shouldCreateFromValue() {
    UUID value = UUID.randomUUID();

    assertEquals(value, ConnectorTenantId.of(value).value());
  }

  @Test
  void shouldRejectNullValue() {
    assertThrows(NullPointerException.class, () -> ConnectorTenantId.of(null));
  }
}
