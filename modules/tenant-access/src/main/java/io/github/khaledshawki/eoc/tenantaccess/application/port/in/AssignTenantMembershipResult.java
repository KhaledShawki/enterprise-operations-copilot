package io.github.khaledshawki.eoc.tenantaccess.application.port.in;

import io.github.khaledshawki.eoc.tenantaccess.domain.model.PlatformUserId;
import io.github.khaledshawki.eoc.tenantaccess.domain.model.TenantId;
import io.github.khaledshawki.eoc.tenantaccess.domain.model.TenantMembershipId;
import io.github.khaledshawki.eoc.tenantaccess.domain.model.TenantMembershipStatus;

public record AssignTenantMembershipResult(
    TenantMembershipId membershipId,
    TenantId tenantId,
    PlatformUserId platformUserId,
    TenantMembershipStatus status) {}
