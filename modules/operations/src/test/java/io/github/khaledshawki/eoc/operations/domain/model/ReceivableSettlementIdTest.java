package io.github.khaledshawki.eoc.operations.domain.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class ReceivableSettlementIdTest {

  @Test
  void shouldCreateAndGenerateSettlementIdentities() {
    UUID value = UUID.fromString("00000000-0000-0000-0000-000000000701");

    assertEquals(value, ReceivableSettlementId.of(value).value());
    assertNotEquals(ReceivableSettlementId.generate(), ReceivableSettlementId.generate());
  }

  @Test
  void shouldRejectMissingSettlementIdentity() {
    assertThrows(NullPointerException.class, () -> ReceivableSettlementId.of(null));
  }
}
