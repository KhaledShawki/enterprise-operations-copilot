package io.github.khaledshawki.eoc.platform.security.adapter.in.web;

import io.github.khaledshawki.eoc.platform.security.model.AuthenticatedUser;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

@Component
public class JwtAuthenticatedUserMapper {

  private static final String ROLE_AUTHORITY_PREFIX = "ROLE_";

  public AuthenticatedUser map(JwtAuthenticationToken authentication) {
    Objects.requireNonNull(authentication, "Authentication cannot be null");

    Set<String> roles =
        authentication.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .filter(Objects::nonNull)
            .filter(authority -> authority.startsWith(ROLE_AUTHORITY_PREFIX))
            .map(authority -> authority.substring(ROLE_AUTHORITY_PREFIX.length()))
            .filter(role -> !role.isBlank())
            .collect(Collectors.toUnmodifiableSet());

    return new AuthenticatedUser(
        issuerUri(authentication), authentication.getToken().getSubject(), roles);
  }

  private static URI issuerUri(JwtAuthenticationToken authentication) {
    URL issuer =
        Objects.requireNonNull(
            authentication.getToken().getIssuer(), "JWT issuer claim cannot be null");

    try {
      return issuer.toURI();
    } catch (URISyntaxException exception) {
      throw new IllegalArgumentException("JWT issuer claim must be a valid URI", exception);
    }
  }
}
