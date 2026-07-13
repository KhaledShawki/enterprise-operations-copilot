package io.github.khaledshawki.eoc.tenantaccess.application.service;

import io.github.khaledshawki.eoc.tenantaccess.application.exception.TenantKeyAlreadyExistsException;
import io.github.khaledshawki.eoc.tenantaccess.application.port.in.CreateTenantCommand;
import io.github.khaledshawki.eoc.tenantaccess.application.port.in.CreateTenantResult;
import io.github.khaledshawki.eoc.tenantaccess.application.port.in.CreateTenantUseCase;
import io.github.khaledshawki.eoc.tenantaccess.application.port.out.TenantRepository;
import io.github.khaledshawki.eoc.tenantaccess.domain.model.Tenant;
import io.github.khaledshawki.eoc.tenantaccess.domain.model.TenantKey;
import io.github.khaledshawki.eoc.tenantaccess.domain.model.TenantName;
import java.util.Objects;

public final class CreateTenantService implements CreateTenantUseCase {

  private final TenantRepository tenantRepository;

  public CreateTenantService(TenantRepository tenantRepository) {
    Objects.requireNonNull(tenantRepository, "Tenant repository cannot be null");

    this.tenantRepository = tenantRepository;
  }

  @Override
  public CreateTenantResult create(CreateTenantCommand command) {
    Objects.requireNonNull(command, "Command cannot be null");

    TenantKey tenantKey = TenantKey.of(command.tenantKey());
    TenantName tenantName = TenantName.of(command.tenantName());

    if (tenantRepository.existsByKey(tenantKey)) {
      throw new TenantKeyAlreadyExistsException(tenantKey);
    }

    Tenant tenant = tenantRepository.save(Tenant.create(tenantKey, tenantName));

    return new CreateTenantResult(tenant.id(), tenant.key(), tenant.name(), tenant.status());
  }
}
