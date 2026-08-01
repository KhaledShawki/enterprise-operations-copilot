package io.github.khaledshawki.eoc.connectormanagement.domain.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class ConnectorTypeTest {

  @Test
  void shouldNormalizeExtensibleTypeKey() {
    assertEquals("sap-b1", ConnectorType.of("  SAP-B1  ").value());
    assertEquals("future-erp-v2", ConnectorType.of("future-erp-v2").value());
  }

  @Test
  void shouldAcceptMaximumLength() {
    String value = "a".repeat(ConnectorType.MAX_LENGTH);

    assertEquals(value, ConnectorType.of(value).value());
  }

  @Test
  void shouldRejectNullEmptyOversizedAndMalformedValues() {
    assertThrows(NullPointerException.class, () -> ConnectorType.of(null));
    assertThrows(IllegalArgumentException.class, () -> ConnectorType.of(" "));
    assertThrows(
        IllegalArgumentException.class,
        () -> ConnectorType.of("a".repeat(ConnectorType.MAX_LENGTH + 1)));
    assertThrows(IllegalArgumentException.class, () -> ConnectorType.of("sap_b1"));
    assertThrows(IllegalArgumentException.class, () -> ConnectorType.of("-sap-b1"));
    assertThrows(IllegalArgumentException.class, () -> ConnectorType.of("sap--b1"));
  }
}
