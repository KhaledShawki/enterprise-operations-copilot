package io.github.khaledshawki.eoc.platform.tenantaccess.adapter.in.web;

import jakarta.validation.constraints.NotNull;
import java.util.Set;

public record ReplaceTenantMembershipRolesRequest(
    @NotNull(message = "Roles are required")
        Set<@NotNull(message = "Role cannot be null") String> roles) {}
