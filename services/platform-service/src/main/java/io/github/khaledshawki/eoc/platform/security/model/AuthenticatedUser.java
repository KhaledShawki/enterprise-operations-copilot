package io.github.khaledshawki.eoc.platform.security.model;

import java.net.URI;
import java.util.Objects;
import java.util.Set;

public record AuthenticatedUser(URI issuer, String subject, Set<String> roles) {

  public AuthenticatedUser {
    Objects.requireNonNull(issuer, "Issuer cannot be null");

    if (!issuer.isAbsolute()) {
      throw new IllegalArgumentException("Issuer must be an absolute URI");
    }

    Objects.requireNonNull(subject, "Subject cannot be null");

    if (subject.isBlank()) {
      throw new IllegalArgumentException("Subject cannot be blank");
    }

    Objects.requireNonNull(roles, "Roles cannot be null");

    if (roles.stream().anyMatch(role -> role == null || role.isBlank())) {
      throw new IllegalArgumentException("Roles cannot contain null or blank values");
    }

    roles = Set.copyOf(roles);
  }
}
