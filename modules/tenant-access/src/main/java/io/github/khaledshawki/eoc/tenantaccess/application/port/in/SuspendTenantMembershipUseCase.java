package io.github.khaledshawki.eoc.tenantaccess.application.port.in;

public interface SuspendTenantMembershipUseCase {

  SuspendTenantMembershipResult suspend(SuspendTenantMembershipCommand command);
}
