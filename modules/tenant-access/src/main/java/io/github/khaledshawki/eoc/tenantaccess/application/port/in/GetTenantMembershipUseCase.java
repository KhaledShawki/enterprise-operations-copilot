package io.github.khaledshawki.eoc.tenantaccess.application.port.in;

public interface GetTenantMembershipUseCase {

  GetTenantMembershipResult get(GetTenantMembershipQuery query);
}
