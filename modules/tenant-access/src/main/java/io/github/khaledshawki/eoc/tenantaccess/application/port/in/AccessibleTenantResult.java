package io.github.khaledshawki.eoc.tenantaccess.application.port.in;

import io.github.khaledshawki.eoc.tenantaccess.domain.model.TenantId;
import io.github.khaledshawki.eoc.tenantaccess.domain.model.TenantKey;
import io.github.khaledshawki.eoc.tenantaccess.domain.model.TenantMembershipId;
import io.github.khaledshawki.eoc.tenantaccess.domain.model.TenantName;
import io.github.khaledshawki.eoc.tenantaccess.domain.model.TenantRoleKey;
import java.util.Objects;
import java.util.Set;

public record AccessibleTenantResult(
    TenantMembershipId membershipId,
    TenantId tenantId,
    TenantKey key,
    TenantName name,
    Set<TenantRoleKey> roles) {

  public AccessibleTenantResult {
    Objects.requireNonNull(membershipId, "Tenant membership id cannot be null");

    Objects.requireNonNull(tenantId, "Tenant id cannot be null");

    Objects.requireNonNull(key, "Tenant key cannot be null");

    Objects.requireNonNull(name, "Tenant name cannot be null");

    roles = Set.copyOf(Objects.requireNonNull(roles, "Tenant membership roles cannot be null"));
  }

  public AccessibleTenantResult(
      TenantMembershipId membershipId, TenantId tenantId, TenantKey key, TenantName name) {
    this(membershipId, tenantId, key, name, Set.of());
  }
}
