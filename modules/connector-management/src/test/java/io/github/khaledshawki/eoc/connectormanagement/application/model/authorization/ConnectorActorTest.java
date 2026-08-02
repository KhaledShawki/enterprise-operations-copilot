package io.github.khaledshawki.eoc.connectormanagement.application.model.authorization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class ConnectorActorTest {

  @Test
  void shouldPreserveExternalIdentity() {
    ConnectorActor actor =
        new ConnectorActor("https://identity.example.com/realms/eoc", "connector-administrator");

    assertEquals("https://identity.example.com/realms/eoc", actor.issuer());
    assertEquals("connector-administrator", actor.subject());
  }

  @Test
  void shouldRejectMissingIdentityComponents() {
    assertThrows(NullPointerException.class, () -> new ConnectorActor(null, "subject"));
    assertThrows(NullPointerException.class, () -> new ConnectorActor("issuer", null));
    assertThrows(IllegalArgumentException.class, () -> new ConnectorActor("", "subject"));
    assertThrows(IllegalArgumentException.class, () -> new ConnectorActor("issuer", ""));
    assertThrows(IllegalArgumentException.class, () -> new ConnectorActor(" ", "subject"));
    assertThrows(IllegalArgumentException.class, () -> new ConnectorActor("issuer", " "));
  }
}
