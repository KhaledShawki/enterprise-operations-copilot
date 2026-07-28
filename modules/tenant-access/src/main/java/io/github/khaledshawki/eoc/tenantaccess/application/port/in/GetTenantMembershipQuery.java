package io.github.khaledshawki.eoc.tenantaccess.application.port.in;

import java.util.Objects;
import java.util.UUID;

public record GetTenantMembershipQuery(UUID tenantId, UUID membershipId) {

  public GetTenantMembershipQuery {
    Objects.requireNonNull(tenantId, "Tenant id cannot be null");
    Objects.requireNonNull(membershipId, "Tenant membership id cannot be null");
  }
}
