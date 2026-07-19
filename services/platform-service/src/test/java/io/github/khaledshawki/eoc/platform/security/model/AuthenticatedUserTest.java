package io.github.khaledshawki.eoc.platform.security.model;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

public class AuthenticatedUserTest {

  private static final URI ISSUER = URI.create("http://localhost:8180/realms/eoc");
  private static final String SUBJECT = "user-123";

  @Test
  void shouldCreateAuthenticatedUser() {
    Set<String> roles = Set.of("platform-admin", "auditor");

    AuthenticatedUser authenticatedUser = new AuthenticatedUser(ISSUER, SUBJECT, roles);

    assertAll(
        () -> assertEquals(ISSUER, authenticatedUser.issuer()),
        () -> assertEquals(SUBJECT, authenticatedUser.subject()),
        () -> assertEquals(roles, authenticatedUser.roles()));
  }

  @Test
  void shouldAllowAuthenticatedUserWithoutRoles() {
    AuthenticatedUser authenticatedUser = new AuthenticatedUser(ISSUER, SUBJECT, Set.of());

    assertTrue(authenticatedUser.roles().isEmpty());
  }

  @Test
  void shouldDefensivelyCopyRoles() {
    Set<String> roles = new HashSet<>();
    roles.add("platform-admin");

    AuthenticatedUser authenticatedUser = new AuthenticatedUser(ISSUER, SUBJECT, roles);

    roles.add("auditor");

    assertEquals(Set.of("platform-admin"), authenticatedUser.roles());
    assertThrows(
        UnsupportedOperationException.class, () -> authenticatedUser.roles().add("another-role"));
  }

  @Test
  void shouldRejectNullIssuer() {
    assertThrows(NullPointerException.class, () -> new AuthenticatedUser(null, SUBJECT, Set.of()));
  }

  @Test
  void shouldRejectRelativeIssuer() {
    URI relativeIssuer = URI.create("realms/eoc");

    assertThrows(
        IllegalArgumentException.class,
        () -> new AuthenticatedUser(relativeIssuer, SUBJECT, Set.of()));
  }

  @Test
  void shouldRejectNullOrBlankSubject() {
    assertAll(
        () ->
            assertThrows(
                NullPointerException.class, () -> new AuthenticatedUser(ISSUER, null, Set.of())),
        () ->
            assertThrows(
                IllegalArgumentException.class, () -> new AuthenticatedUser(ISSUER, "", Set.of())),
        () ->
            assertThrows(
                IllegalArgumentException.class,
                () -> new AuthenticatedUser(ISSUER, "   ", Set.of())));
  }

  @Test
  void shouldRejectNullOrInvalidRoles() {
    Set<String> rolesContainingNull = new HashSet<>();
    rolesContainingNull.add(null);

    assertAll(
        () ->
            assertThrows(
                NullPointerException.class, () -> new AuthenticatedUser(ISSUER, SUBJECT, null)),
        () ->
            assertThrows(
                IllegalArgumentException.class,
                () -> new AuthenticatedUser(ISSUER, SUBJECT, Set.of(""))),
        () ->
            assertThrows(
                IllegalArgumentException.class,
                () -> new AuthenticatedUser(ISSUER, SUBJECT, Set.of("   "))),
        () ->
            assertThrows(
                IllegalArgumentException.class,
                () -> new AuthenticatedUser(ISSUER, SUBJECT, rolesContainingNull)));
  }
}
