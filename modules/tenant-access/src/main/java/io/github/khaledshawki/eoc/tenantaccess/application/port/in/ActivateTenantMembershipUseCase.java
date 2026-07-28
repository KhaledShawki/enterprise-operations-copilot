package io.github.khaledshawki.eoc.tenantaccess.application.port.in;

public interface ActivateTenantMembershipUseCase {

  ActivateTenantMembershipResult activate(ActivateTenantMembershipCommand command);
}
