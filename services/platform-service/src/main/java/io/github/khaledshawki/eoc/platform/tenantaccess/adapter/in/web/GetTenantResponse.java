package io.github.khaledshawki.eoc.platform.tenantaccess.adapter.in.web;

import io.github.khaledshawki.eoc.tenantaccess.application.port.in.GetTenantResult;
import java.util.Objects;
import java.util.UUID;

public record GetTenantResponse(UUID id, String tenantKey, String displayName, String status) {

  static GetTenantResponse from(GetTenantResult result) {
    Objects.requireNonNull(result, "Get tenant result cannot be null");

    return new GetTenantResponse(
        result.tenantId().value(),
        result.key().value(),
        result.name().value(),
        result.status().name());
  }
}
