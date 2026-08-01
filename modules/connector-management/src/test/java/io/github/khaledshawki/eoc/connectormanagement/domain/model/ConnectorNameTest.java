package io.github.khaledshawki.eoc.connectormanagement.domain.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class ConnectorNameTest {

  @Test
  void shouldTrimValue() {
    assertEquals("Primary ERP", ConnectorName.of("  Primary ERP  ").value());
  }

  @Test
  void shouldAcceptMaximumLength() {
    String value = "a".repeat(ConnectorName.MAX_LENGTH);

    assertEquals(value, ConnectorName.of(value).value());
  }

  @Test
  void shouldRejectNullEmptyAndOversizedValues() {
    assertThrows(NullPointerException.class, () -> ConnectorName.of(null));
    assertThrows(IllegalArgumentException.class, () -> ConnectorName.of("  "));
    assertThrows(
        IllegalArgumentException.class,
        () -> ConnectorName.of("a".repeat(ConnectorName.MAX_LENGTH + 1)));
  }
}
