package io.github.khaledshawki.eoc.platform.tenantaccess.adapter.in.web;

import io.github.khaledshawki.eoc.tenantaccess.domain.model.TenantKey;
import io.github.khaledshawki.eoc.tenantaccess.domain.model.TenantName;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateTenantRequest(
    @NotBlank(message = "Tenant key is required")
        @Size(
            max = TenantKey.MAX_LENGTH,
            message = "Tenant key cannot be longer than {max} characters")
        @Pattern(
            regexp = "^[a-z0-9]+(?:-[a-z0-9]+)*$",
            message = "Tenant key must contain lowercase letters, numbers, and single hyphens only")
        String tenantKey,
    @NotBlank(message = "Display name is required")
        @Size(
            max = TenantName.MAX_LENGTH,
            message = "Display name cannot be longer than {max} characters")
        String displayName) {}
