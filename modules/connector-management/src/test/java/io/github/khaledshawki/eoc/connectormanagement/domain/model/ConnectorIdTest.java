package io.github.khaledshawki.eoc.connectormanagement.domain.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class ConnectorIdTest {

  @Test
  void shouldCreateFromValue() {
    UUID value = UUID.randomUUID();

    assertEquals(value, ConnectorId.of(value).value());
  }

  @Test
  void shouldGenerateValue() {
    assertNotNull(ConnectorId.generate().value());
  }

  @Test
  void shouldRejectNullValue() {
    assertThrows(NullPointerException.class, () -> ConnectorId.of(null));
  }
}
