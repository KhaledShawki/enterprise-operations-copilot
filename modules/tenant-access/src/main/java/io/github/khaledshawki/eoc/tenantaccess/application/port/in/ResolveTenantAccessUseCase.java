package io.github.khaledshawki.eoc.tenantaccess.application.port.in;

@FunctionalInterface
public interface ResolveTenantAccessUseCase {

  ResolveTenantAccessResult resolve(ResolveTenantAccessQuery query);
}
