package io.github.khaledshawki.eoc.tenantaccess.application.service;

import io.github.khaledshawki.eoc.tenantaccess.application.exception.TenantNotFoundException;
import io.github.khaledshawki.eoc.tenantaccess.application.port.in.GetTenantQuery;
import io.github.khaledshawki.eoc.tenantaccess.application.port.in.GetTenantResult;
import io.github.khaledshawki.eoc.tenantaccess.application.port.in.GetTenantUseCase;
import io.github.khaledshawki.eoc.tenantaccess.application.port.out.TenantRepository;
import io.github.khaledshawki.eoc.tenantaccess.domain.model.Tenant;
import io.github.khaledshawki.eoc.tenantaccess.domain.model.TenantId;
import java.util.Objects;

public final class GetTenantService implements GetTenantUseCase {

  private final TenantRepository tenantRepository;

  public GetTenantService(TenantRepository tenantRepository) {
    this.tenantRepository =
        Objects.requireNonNull(tenantRepository, "Tenant repository cannot be null");
  }

  @Override
  public GetTenantResult get(GetTenantQuery query) {
    Objects.requireNonNull(query, "Query cannot be null");

    TenantId tenantId = TenantId.of(query.tenantId());

    Tenant tenant =
        tenantRepository
            .findById(tenantId)
            .orElseThrow(() -> new TenantNotFoundException(tenantId));

    return new GetTenantResult(tenant.id(), tenant.key(), tenant.name(), tenant.status());
  }
}
