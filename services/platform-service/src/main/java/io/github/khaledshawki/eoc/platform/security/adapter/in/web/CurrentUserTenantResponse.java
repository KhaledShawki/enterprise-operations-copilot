package io.github.khaledshawki.eoc.platform.security.adapter.in.web;

import io.github.khaledshawki.eoc.tenantaccess.application.port.in.AccessibleTenantResult;
import java.util.Objects;
import java.util.UUID;

record CurrentUserTenantResponse(
    UUID membershipId, UUID tenantId, String tenantKey, String displayName) {

  static CurrentUserTenantResponse from(AccessibleTenantResult result) {
    Objects.requireNonNull(result, "Accessible tenant result cannot be null");

    return new CurrentUserTenantResponse(
        result.membershipId().value(),
        result.tenantId().value(),
        result.key().value(),
        result.name().value());
  }
}
