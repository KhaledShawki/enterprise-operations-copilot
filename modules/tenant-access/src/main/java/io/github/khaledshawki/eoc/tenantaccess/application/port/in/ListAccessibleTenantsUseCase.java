package io.github.khaledshawki.eoc.tenantaccess.application.port.in;

public interface ListAccessibleTenantsUseCase {

  ListAccessibleTenantsResult list(ListAccessibleTenantsQuery query);
}
