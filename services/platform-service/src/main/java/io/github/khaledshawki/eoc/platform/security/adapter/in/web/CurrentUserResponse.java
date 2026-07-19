package io.github.khaledshawki.eoc.platform.security.adapter.in.web;

import io.github.khaledshawki.eoc.platform.security.model.AuthenticatedUser;
import java.util.List;
import java.util.Objects;

record CurrentUserResponse(String issuer, String subject, List<String> roles) {

  static CurrentUserResponse from(AuthenticatedUser authenticatedUser) {
    Objects.requireNonNull(authenticatedUser, "Authenticated user cannot be null");

    List<String> sortedRoles = authenticatedUser.roles().stream().sorted().toList();

    return new CurrentUserResponse(
        authenticatedUser.issuer().toString(), authenticatedUser.subject(), sortedRoles);
  }
}
