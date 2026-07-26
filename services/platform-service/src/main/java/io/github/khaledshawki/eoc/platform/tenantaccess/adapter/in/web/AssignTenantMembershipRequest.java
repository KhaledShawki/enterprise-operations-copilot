package io.github.khaledshawki.eoc.platform.tenantaccess.adapter.in.web;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record AssignTenantMembershipRequest(
    @NotNull(message = "Platform user id is required") UUID platformUserId) {}
