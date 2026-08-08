package io.github.khaledshawki.eoc.operations.domain.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class ReceivableAllocationIdTest {

  @Test
  void shouldCreateAndGenerateAllocationIdentities() {
    UUID value = UUID.fromString("00000000-0000-0000-0000-000000000801");

    assertEquals(value, ReceivableAllocationId.of(value).value());
    assertNotEquals(ReceivableAllocationId.generate(), ReceivableAllocationId.generate());
  }

  @Test
  void shouldRejectMissingAllocationIdentity() {
    assertThrows(NullPointerException.class, () -> ReceivableAllocationId.of(null));
  }
}
