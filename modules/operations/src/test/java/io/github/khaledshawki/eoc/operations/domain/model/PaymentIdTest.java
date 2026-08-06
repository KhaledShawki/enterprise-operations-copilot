package io.github.khaledshawki.eoc.operations.domain.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class PaymentIdTest {

  @Test
  void shouldCreateAndReconstitutePaymentIds() {
    UUID value = UUID.fromString("00000000-0000-0000-0000-000000000501");

    assertEquals(value, PaymentId.of(value).value());
    assertNotEquals(PaymentId.generate(), PaymentId.generate());
  }

  @Test
  void shouldRejectMissingValue() {
    assertThrows(NullPointerException.class, () -> PaymentId.of(null));
  }
}
