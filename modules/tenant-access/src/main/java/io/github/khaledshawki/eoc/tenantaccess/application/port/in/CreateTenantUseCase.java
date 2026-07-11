package io.github.khaledshawki.eoc.tenantaccess.application.port.in;

public interface CreateTenantUseCase {
  CreateTenantResult create(CreateTenantCommand command);
}
