package io.github.khaledshawki.eoc.tenantaccess.application.port.in;

import io.github.khaledshawki.eoc.tenantaccess.domain.model.TenantId;
import io.github.khaledshawki.eoc.tenantaccess.domain.model.TenantKey;
import io.github.khaledshawki.eoc.tenantaccess.domain.model.TenantName;
import io.github.khaledshawki.eoc.tenantaccess.domain.model.TenantStatus;
import java.util.Objects;

public record GetTenantResult(
    TenantId tenantId, TenantKey key, TenantName name, TenantStatus status) {

  public GetTenantResult {
    Objects.requireNonNull(tenantId, "Tenant id cannot be null");

    Objects.requireNonNull(key, "Tenant key cannot be null");

    Objects.requireNonNull(name, "Tenant name cannot be null");

    Objects.requireNonNull(status, "Tenant status cannot be null");
  }
}
