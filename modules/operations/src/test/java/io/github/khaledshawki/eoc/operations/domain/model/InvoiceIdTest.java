package io.github.khaledshawki.eoc.operations.domain.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class InvoiceIdTest {

  @Test
  void shouldGenerateDistinctNonNullIdentifiers() {
    InvoiceId first = InvoiceId.generate();
    InvoiceId second = InvoiceId.generate();

    assertNotNull(first.value());
    assertNotNull(second.value());
    assertNotEquals(first, second);
  }

  @Test
  void shouldCreateIdentifierFromExistingUuid() {
    UUID value = UUID.fromString("00000000-0000-0000-0000-000000000010");

    assertEquals(value, InvoiceId.of(value).value());
    assertThrows(NullPointerException.class, () -> InvoiceId.of(null));
  }
}
