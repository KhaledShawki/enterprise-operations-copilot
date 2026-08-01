package io.github.khaledshawki.eoc.platform.security.adapter.in.web;

import io.github.khaledshawki.eoc.platform.security.model.AuthenticatedUser;
import io.github.khaledshawki.eoc.tenantaccess.application.port.in.ResolveTenantAccessQuery;
import io.github.khaledshawki.eoc.tenantaccess.application.port.in.ResolveTenantAccessUseCase;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component("tenantAccessPolicy")
public class TenantAccessPolicy {

  private final JwtAuthenticatedUserMapper authenticatedUserMapper;
  private final ResolveTenantAccessUseCase resolveTenantAccessUseCase;

  public TenantAccessPolicy(
      JwtAuthenticatedUserMapper authenticatedUserMapper,
      ResolveTenantAccessUseCase resolveTenantAccessUseCase) {
    this.authenticatedUserMapper =
        Objects.requireNonNull(authenticatedUserMapper, "Authenticated user mapper cannot be null");
    this.resolveTenantAccessUseCase =
        Objects.requireNonNull(
            resolveTenantAccessUseCase, "Resolve tenant access use case cannot be null");
  }

  @Transactional(readOnly = true)
  public boolean hasRole(Authentication authentication, UUID tenantId, String requiredRole) {
    Objects.requireNonNull(tenantId, "Tenant id cannot be null");
    Objects.requireNonNull(requiredRole, "Required tenant role cannot be null");

    return authenticatedUser(authentication)
        .map(user -> hasRole(user, tenantId, requiredRole))
        .orElse(false);
  }

  @Transactional(readOnly = true)
  public boolean hasAnyRole(Authentication authentication, UUID tenantId, String... requiredRoles) {
    Objects.requireNonNull(tenantId, "Tenant id cannot be null");
    Objects.requireNonNull(requiredRoles, "Required tenant roles cannot be null");

    if (requiredRoles.length == 0) {
      throw new IllegalArgumentException("At least one required tenant role must be provided");
    }

    if (Arrays.stream(requiredRoles).anyMatch(Objects::isNull)) {
      throw new IllegalArgumentException("Required tenant roles cannot contain null values");
    }

    return authenticatedUser(authentication)
        .map(
            user ->
                Arrays.stream(requiredRoles)
                    .anyMatch(requiredRole -> hasRole(user, tenantId, requiredRole)))
        .orElse(false);
  }

  private Optional<AuthenticatedUser> authenticatedUser(Authentication authentication) {
    if (!(authentication instanceof JwtAuthenticationToken jwtAuthentication)
        || !authentication.isAuthenticated()) {
      return Optional.empty();
    }

    return Optional.of(authenticatedUserMapper.map(jwtAuthentication));
  }

  private boolean hasRole(AuthenticatedUser authenticatedUser, UUID tenantId, String requiredRole) {
    return resolveTenantAccessUseCase
        .resolve(
            new ResolveTenantAccessQuery(
                authenticatedUser.issuer().toString(),
                authenticatedUser.subject(),
                tenantId,
                requiredRole))
        .granted();
  }
}
