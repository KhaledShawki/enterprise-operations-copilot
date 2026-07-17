package io.github.khaledshawki.eoc.platform.tenantaccess.adapter.in.web;

import io.github.khaledshawki.eoc.tenantaccess.application.port.in.CreateTenantResult;
import java.util.UUID;

public record CreateTenantResponse(UUID id, String tenantKey, String displayName, String status) {

  static CreateTenantResponse from(CreateTenantResult result) {
    return new CreateTenantResponse(
        result.tenantId().value(),
        result.tenantKey().value(),
        result.tenantName().value(),
        result.tenantStatus().name());
  }
}
