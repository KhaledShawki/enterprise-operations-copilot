package io.github.khaledshawki.eoc.tenantaccess.application.port.in;

import java.util.Objects;
import java.util.UUID;

public record ActivateTenantMembershipCommand(UUID tenantId, UUID membershipId) {

  public ActivateTenantMembershipCommand {
    Objects.requireNonNull(tenantId, "Tenant id cannot be null");

    Objects.requireNonNull(membershipId, "Tenant membership id cannot be null");
  }
}
