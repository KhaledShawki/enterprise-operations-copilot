package io.github.khaledshawki.eoc.platform.security.adapter.in.web;

import io.github.khaledshawki.eoc.tenantaccess.application.port.in.AccessibleTenantResult;
import io.github.khaledshawki.eoc.tenantaccess.domain.model.TenantRoleKey;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

record CurrentUserTenantResponse(
    UUID membershipId, UUID tenantId, String tenantKey, String displayName, Set<String> roles) {

  CurrentUserTenantResponse {
    Objects.requireNonNull(roles, "Current user tenant roles cannot be null");

    roles = Set.copyOf(roles);
  }

  static CurrentUserTenantResponse from(AccessibleTenantResult result) {
    Objects.requireNonNull(result, "Accessible tenant result cannot be null");

    return new CurrentUserTenantResponse(
        result.membershipId().value(),
        result.tenantId().value(),
        result.key().value(),
        result.name().value(),
        toRoleValues(result.roles()));
  }

  private static Set<String> toRoleValues(Set<TenantRoleKey> roles) {
    Objects.requireNonNull(roles, "Tenant membership roles cannot be null");

    return roles.stream().map(TenantRoleKey::value).collect(Collectors.toUnmodifiableSet());
  }
}
