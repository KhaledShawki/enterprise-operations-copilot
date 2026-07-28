package io.github.khaledshawki.eoc.tenantaccess.application.port.out;

import io.github.khaledshawki.eoc.tenantaccess.domain.model.TenantId;
import io.github.khaledshawki.eoc.tenantaccess.domain.model.TenantKey;
import io.github.khaledshawki.eoc.tenantaccess.domain.model.TenantMembershipId;
import io.github.khaledshawki.eoc.tenantaccess.domain.model.TenantMembershipStatus;
import io.github.khaledshawki.eoc.tenantaccess.domain.model.TenantName;
import io.github.khaledshawki.eoc.tenantaccess.domain.model.TenantStatus;
import java.util.Objects;

public record AccessibleTenantProjection(
    TenantMembershipId membershipId,
    TenantId tenantId,
    TenantKey key,
    TenantName name,
    TenantStatus tenantStatus,
    TenantMembershipStatus membershipStatus) {

  public AccessibleTenantProjection {
    Objects.requireNonNull(membershipId, "Tenant membership id cannot be null");

    Objects.requireNonNull(tenantId, "Tenant id cannot be null");

    Objects.requireNonNull(key, "Tenant key cannot be null");

    Objects.requireNonNull(name, "Tenant name cannot be null");

    Objects.requireNonNull(tenantStatus, "Tenant status cannot be null");

    Objects.requireNonNull(membershipStatus, "Tenant membership status cannot be null");
  }
}
