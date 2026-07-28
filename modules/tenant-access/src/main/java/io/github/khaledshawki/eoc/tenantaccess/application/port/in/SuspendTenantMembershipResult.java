package io.github.khaledshawki.eoc.tenantaccess.application.port.in;

import io.github.khaledshawki.eoc.tenantaccess.domain.model.PlatformUserId;
import io.github.khaledshawki.eoc.tenantaccess.domain.model.TenantId;
import io.github.khaledshawki.eoc.tenantaccess.domain.model.TenantMembershipId;
import io.github.khaledshawki.eoc.tenantaccess.domain.model.TenantMembershipStatus;
import java.util.Objects;

public record SuspendTenantMembershipResult(
    TenantMembershipId membershipId,
    TenantId tenantId,
    PlatformUserId platformUserId,
    TenantMembershipStatus status) {

  public SuspendTenantMembershipResult {
    Objects.requireNonNull(membershipId, "Tenant membership id cannot be null");
    Objects.requireNonNull(tenantId, "Tenant id cannot be null");
    Objects.requireNonNull(platformUserId, "Platform user id cannot be null");
    Objects.requireNonNull(status, "Tenant membership status cannot be null");
  }
}
