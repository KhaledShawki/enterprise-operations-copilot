package io.github.khaledshawki.eoc.operations.domain.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class InvoiceNumberTest {

  @Test
  void shouldNormalizeInvoiceNumber() {
    assertEquals("INV-100", new InvoiceNumber(" INV-100 ").value());
  }

  @Test
  void shouldRejectMissingBlankAndOversizedInvoiceNumbers() {
    assertThrows(NullPointerException.class, () -> new InvoiceNumber(null));
    assertThrows(IllegalArgumentException.class, () -> new InvoiceNumber(" "));
    assertThrows(
        IllegalArgumentException.class,
        () -> new InvoiceNumber("I".repeat(InvoiceNumber.MAX_LENGTH + 1)));
  }
}
