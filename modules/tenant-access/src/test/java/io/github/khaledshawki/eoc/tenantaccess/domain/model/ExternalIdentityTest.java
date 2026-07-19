package io.github.khaledshawki.eoc.tenantaccess.domain.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class ExternalIdentityTest {

  private static final String ISSUER = "http://localhost:8180/realms/eoc";
  private static final String SUBJECT = "user-123";

  @Test
  void shouldCreateExternalIdentity() {
    ExternalIdentity identity = ExternalIdentity.of(ISSUER, SUBJECT);

    assertEquals(ISSUER, identity.issuer());
    assertEquals(SUBJECT, identity.subject());
  }

  @Test
  void shouldHaveValueSemantics() {
    ExternalIdentity first = ExternalIdentity.of(ISSUER, SUBJECT);
    ExternalIdentity second = ExternalIdentity.of(ISSUER, SUBJECT);

    assertEquals(first, second);
  }

  @Test
  void shouldRejectNullIssuer() {
    assertThrows(NullPointerException.class, () -> ExternalIdentity.of(null, SUBJECT));
  }

  @Test
  void shouldRejectBlankIssuer() {
    assertThrows(IllegalArgumentException.class, () -> ExternalIdentity.of(" ", SUBJECT));
  }

  @Test
  void shouldRejectNullSubject() {
    assertThrows(NullPointerException.class, () -> ExternalIdentity.of(ISSUER, null));
  }

  @Test
  void shouldRejectBlankSubject() {
    assertThrows(IllegalArgumentException.class, () -> ExternalIdentity.of(ISSUER, " "));
  }
}
