package io.github.khaledshawki.eoc.platform.security.adapter.in.web;

import io.github.khaledshawki.eoc.tenantaccess.application.port.in.ListAccessibleTenantsResult;
import java.util.List;
import java.util.Objects;

record CurrentUserTenantsResponse(List<CurrentUserTenantResponse> tenants) {

  CurrentUserTenantsResponse {
    Objects.requireNonNull(tenants, "Current user tenants cannot be null");

    if (tenants.stream().anyMatch(Objects::isNull)) {
      throw new IllegalArgumentException("Current user tenants cannot contain null values");
    }

    tenants = List.copyOf(tenants);
  }

  static CurrentUserTenantsResponse from(ListAccessibleTenantsResult result) {
    Objects.requireNonNull(result, "List accessible tenants result cannot be null");

    return new CurrentUserTenantsResponse(
        result.tenants().stream().map(CurrentUserTenantResponse::from).toList());
  }
}
