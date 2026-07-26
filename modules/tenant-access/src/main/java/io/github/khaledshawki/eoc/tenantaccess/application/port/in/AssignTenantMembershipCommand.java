package io.github.khaledshawki.eoc.tenantaccess.application.port.in;

import java.util.Objects;
import java.util.UUID;

public record AssignTenantMembershipCommand(UUID tenantId, UUID platformUserId) {

  public AssignTenantMembershipCommand {
    Objects.requireNonNull(tenantId, "Tenant id cannot be null");
    Objects.requireNonNull(platformUserId, "Platform user id cannot be null");
  }
}
