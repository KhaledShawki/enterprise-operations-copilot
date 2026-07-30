package io.github.khaledshawki.eoc.tenantaccess.application.port.in;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public record ReplaceTenantMembershipRolesCommand(
    UUID tenantId, UUID membershipId, Set<String> roles) {

  public ReplaceTenantMembershipRolesCommand {
    Objects.requireNonNull(tenantId, "Tenant id cannot be null");

    Objects.requireNonNull(membershipId, "Tenant membership id cannot be null");

    Objects.requireNonNull(roles, "Tenant membership roles cannot be null");

    if (roles.stream().anyMatch(Objects::isNull)) {
      throw new IllegalArgumentException("Tenant membership roles cannot contain null values");
    }

    roles = Set.copyOf(roles);
  }
}
