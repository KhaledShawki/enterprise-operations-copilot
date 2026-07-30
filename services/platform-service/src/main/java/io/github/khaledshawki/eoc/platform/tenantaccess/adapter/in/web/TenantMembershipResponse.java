package io.github.khaledshawki.eoc.platform.tenantaccess.adapter.in.web;

import io.github.khaledshawki.eoc.tenantaccess.application.port.in.ActivateTenantMembershipResult;
import io.github.khaledshawki.eoc.tenantaccess.application.port.in.AssignTenantMembershipResult;
import io.github.khaledshawki.eoc.tenantaccess.application.port.in.GetTenantMembershipResult;
import io.github.khaledshawki.eoc.tenantaccess.application.port.in.ReplaceTenantMembershipRolesResult;
import io.github.khaledshawki.eoc.tenantaccess.application.port.in.SuspendTenantMembershipResult;
import io.github.khaledshawki.eoc.tenantaccess.domain.model.TenantRoleKey;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public record TenantMembershipResponse(
    UUID id, UUID tenantId, UUID platformUserId, String status, Set<String> roles) {

  public TenantMembershipResponse {
    Objects.requireNonNull(id, "Tenant membership id cannot be null");
    Objects.requireNonNull(tenantId, "Tenant id cannot be null");
    Objects.requireNonNull(platformUserId, "Platform user id cannot be null");
    Objects.requireNonNull(status, "Tenant membership status cannot be null");
    Objects.requireNonNull(roles, "Tenant membership roles cannot be null");

    roles = Set.copyOf(roles);
  }

  static TenantMembershipResponse from(AssignTenantMembershipResult result) {
    Objects.requireNonNull(result, "Assign tenant membership result cannot be null");

    return new TenantMembershipResponse(
        result.membershipId().value(),
        result.tenantId().value(),
        result.platformUserId().value(),
        result.status().name(),
        toRoleValues(result.roles()));
  }

  static TenantMembershipResponse from(GetTenantMembershipResult result) {
    Objects.requireNonNull(result, "Get tenant membership result cannot be null");

    return new TenantMembershipResponse(
        result.membershipId().value(),
        result.tenantId().value(),
        result.platformUserId().value(),
        result.status().name(),
        toRoleValues(result.roles()));
  }

  static TenantMembershipResponse from(SuspendTenantMembershipResult result) {
    Objects.requireNonNull(result, "Suspend tenant membership result cannot be null");

    return new TenantMembershipResponse(
        result.membershipId().value(),
        result.tenantId().value(),
        result.platformUserId().value(),
        result.status().name(),
        toRoleValues(result.roles()));
  }

  static TenantMembershipResponse from(ActivateTenantMembershipResult result) {
    Objects.requireNonNull(result, "Activate tenant membership result cannot be null");

    return new TenantMembershipResponse(
        result.membershipId().value(),
        result.tenantId().value(),
        result.platformUserId().value(),
        result.status().name(),
        toRoleValues(result.roles()));
  }

  static TenantMembershipResponse from(ReplaceTenantMembershipRolesResult result) {
    Objects.requireNonNull(result, "Replace tenant membership roles result cannot be null");

    return new TenantMembershipResponse(
        result.membershipId().value(),
        result.tenantId().value(),
        result.platformUserId().value(),
        result.status().name(),
        toRoleValues(result.roles()));
  }

  private static Set<String> toRoleValues(Set<TenantRoleKey> roles) {
    Objects.requireNonNull(roles, "Tenant membership roles cannot be null");

    return roles.stream().map(TenantRoleKey::value).collect(Collectors.toUnmodifiableSet());
  }
}
