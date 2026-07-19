package io.github.khaledshawki.eoc.platform.security.adapter.in.web;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.khaledshawki.eoc.platform.security.model.AuthenticatedUser;
import java.net.URI;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class JwtAuthenticatedUserMapperTest {

  private static final URI ISSUER = URI.create("http://localhost:8180/realms/eoc");
  private static final String SUBJECT = "user-123";

  private final JwtAuthenticatedUserMapper mapper = new JwtAuthenticatedUserMapper();

  @Test
  void shouldMapIdentityAndRoleAuthorities() {
    JwtAuthenticationToken authentication =
        new JwtAuthenticationToken(
            jwt(),
            List.of(
                new SimpleGrantedAuthority("ROLE_platform-admin"),
                new SimpleGrantedAuthority("ROLE_auditor"),
                new SimpleGrantedAuthority("SCOPE_profile")));

    AuthenticatedUser authenticatedUser = mapper.map(authentication);

    assertAll(
        () -> assertEquals(ISSUER, authenticatedUser.issuer()),
        () -> assertEquals(SUBJECT, authenticatedUser.subject()),
        () -> assertEquals(Set.of("platform-admin", "auditor"), authenticatedUser.roles()));
  }

  @Test
  void shouldIgnoreAuthoritiesThatAreNotValidRoles() {
    JwtAuthenticationToken authentication =
        new JwtAuthenticationToken(
            jwt(),
            List.of(
                new SimpleGrantedAuthority("SCOPE_profile"),
                new SimpleGrantedAuthority("permission:read"),
                new SimpleGrantedAuthority("ROLE_")));

    AuthenticatedUser authenticatedUser = mapper.map(authentication);

    assertTrue(authenticatedUser.roles().isEmpty());
  }

  @Test
  void shouldRejectNullAuthentication() {
    assertThrows(NullPointerException.class, () -> mapper.map(null));
  }

  private static Jwt jwt() {
    return Jwt.withTokenValue("access-token")
        .header("alg", "RS256")
        .issuer(ISSUER.toString())
        .subject(SUBJECT)
        .build();
  }
}
