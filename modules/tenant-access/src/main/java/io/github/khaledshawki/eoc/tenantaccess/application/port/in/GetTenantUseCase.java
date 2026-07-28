package io.github.khaledshawki.eoc.tenantaccess.application.port.in;

public interface GetTenantUseCase {

  GetTenantResult get(GetTenantQuery query);
}
