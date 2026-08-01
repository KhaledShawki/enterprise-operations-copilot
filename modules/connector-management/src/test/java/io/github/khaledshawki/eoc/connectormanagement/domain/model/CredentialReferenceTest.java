package io.github.khaledshawki.eoc.connectormanagement.domain.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class CredentialReferenceTest {

  @Test
  void shouldRepresentOnlyAnOpaqueIdentifier() {
    UUID value = UUID.randomUUID();

    assertEquals(value, CredentialReference.of(value).value());
  }

  @Test
  void shouldRejectNullValue() {
    assertThrows(NullPointerException.class, () -> CredentialReference.of(null));
  }
}
