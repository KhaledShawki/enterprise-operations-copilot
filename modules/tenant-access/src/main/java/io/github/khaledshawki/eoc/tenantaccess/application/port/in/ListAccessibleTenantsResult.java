package io.github.khaledshawki.eoc.tenantaccess.application.port.in;

import java.util.List;
import java.util.Objects;

public record ListAccessibleTenantsResult(List<AccessibleTenantResult> tenants) {

  public ListAccessibleTenantsResult {
    Objects.requireNonNull(tenants, "Accessible tenants cannot be null");

    if (tenants.stream().anyMatch(Objects::isNull)) {
      throw new IllegalArgumentException("Accessible tenants cannot contain null values");
    }

    tenants = List.copyOf(tenants);
  }
}
