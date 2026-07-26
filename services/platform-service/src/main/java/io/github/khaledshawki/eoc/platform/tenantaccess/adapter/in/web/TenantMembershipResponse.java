package io.github.khaledshawki.eoc.platform.tenantaccess.adapter.in.web;

import io.github.khaledshawki.eoc.tenantaccess.application.port.in.AssignTenantMembershipResult;
import java.util.Objects;
import java.util.UUID;

public record TenantMembershipResponse(UUID id, UUID tenantId, UUID platformUserId, String status) {

  static TenantMembershipResponse from(AssignTenantMembershipResult result) {
    Objects.requireNonNull(result, "Assign tenant membership result cannot be null");

    return new TenantMembershipResponse(
        result.membershipId().value(),
        result.tenantId().value(),
        result.platformUserId().value(),
        result.status().name());
  }
}
