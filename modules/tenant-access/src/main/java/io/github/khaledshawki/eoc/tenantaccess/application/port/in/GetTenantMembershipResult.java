package io.github.khaledshawki.eoc.tenantaccess.application.port.in;

import io.github.khaledshawki.eoc.tenantaccess.domain.model.PlatformUserId;
import io.github.khaledshawki.eoc.tenantaccess.domain.model.TenantId;
import io.github.khaledshawki.eoc.tenantaccess.domain.model.TenantMembershipId;
import io.github.khaledshawki.eoc.tenantaccess.domain.model.TenantMembershipStatus;
import io.github.khaledshawki.eoc.tenantaccess.domain.model.TenantRoleKey;
import java.util.Objects;
import java.util.Set;

public record GetTenantMembershipResult(
    TenantMembershipId membershipId,
    TenantId tenantId,
    PlatformUserId platformUserId,
    TenantMembershipStatus status,
    Set<TenantRoleKey> roles) {

  public GetTenantMembershipResult {
    Objects.requireNonNull(membershipId, "Tenant membership id cannot be null");
    Objects.requireNonNull(tenantId, "Tenant id cannot be null");
    Objects.requireNonNull(platformUserId, "Platform user id cannot be null");
    Objects.requireNonNull(status, "Tenant membership status cannot be null");
    roles = Set.copyOf(Objects.requireNonNull(roles, "Tenant membership roles cannot be null"));
  }

  public GetTenantMembershipResult(
      TenantMembershipId membershipId,
      TenantId tenantId,
      PlatformUserId platformUserId,
      TenantMembershipStatus status) {
    this(membershipId, tenantId, platformUserId, status, Set.of());
  }
}
