package io.github.khaledshawki.eoc.tenantaccess.application.exception;

import io.github.khaledshawki.eoc.tenantaccess.domain.model.TenantId;
import io.github.khaledshawki.eoc.tenantaccess.domain.model.TenantMembershipId;
import java.util.Objects;

public final class TenantMembershipNotFoundException extends RuntimeException {

  public TenantMembershipNotFoundException(TenantId tenantId, TenantMembershipId membershipId) {
    super(message(tenantId, membershipId));
  }

  private static String message(TenantId tenantId, TenantMembershipId membershipId) {
    Objects.requireNonNull(tenantId, "Tenant id cannot be null");
    Objects.requireNonNull(membershipId, "Tenant membership id cannot be null");

    return "Tenant membership "
        + membershipId.value()
        + " was not found for tenant "
        + tenantId.value();
  }
}
