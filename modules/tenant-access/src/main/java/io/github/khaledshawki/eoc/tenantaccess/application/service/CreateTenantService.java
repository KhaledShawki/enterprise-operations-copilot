package io.github.khaledshawki.eoc.tenantaccess.application.service;

import io.github.khaledshawki.eoc.tenantaccess.application.exception.TenantNameAlreadyExistsException;
import io.github.khaledshawki.eoc.tenantaccess.application.port.in.CreateTenantCommand;
import io.github.khaledshawki.eoc.tenantaccess.application.port.in.CreateTenantResult;
import io.github.khaledshawki.eoc.tenantaccess.application.port.in.CreateTenantUseCase;
import io.github.khaledshawki.eoc.tenantaccess.application.port.out.TenantRepository;
import io.github.khaledshawki.eoc.tenantaccess.domain.model.Tenant;
import io.github.khaledshawki.eoc.tenantaccess.domain.model.TenantName;

public final class CreateTenantService implements CreateTenantUseCase {

  private final TenantRepository tenantRepository;

  public CreateTenantService(TenantRepository tenantRepository) {
    if (tenantRepository == null) {
      throw new IllegalArgumentException("Tenant repository cannot be null");
    }

    this.tenantRepository = tenantRepository;
  }

  @Override
  public CreateTenantResult create(CreateTenantCommand command) {
    if (command == null) {
      throw new IllegalArgumentException("Command cannot be null");
    }

    TenantName tenantName = TenantName.of(command.tenantName());

    if (tenantRepository.existsByName(tenantName)) {
      throw new TenantNameAlreadyExistsException(tenantName);
    }

    Tenant tenant = tenantRepository.save(Tenant.create(tenantName));

    return new CreateTenantResult(tenant.id(), tenant.name(), tenant.status());
  }
}
