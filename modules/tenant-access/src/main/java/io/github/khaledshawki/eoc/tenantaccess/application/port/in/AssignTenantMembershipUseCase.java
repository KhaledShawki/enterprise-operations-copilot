package io.github.khaledshawki.eoc.tenantaccess.application.port.in;

public interface AssignTenantMembershipUseCase {

  AssignTenantMembershipResult assign(AssignTenantMembershipCommand command);
}
