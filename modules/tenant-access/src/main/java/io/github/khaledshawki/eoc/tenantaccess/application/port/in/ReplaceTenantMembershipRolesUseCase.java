package io.github.khaledshawki.eoc.tenantaccess.application.port.in;

public interface ReplaceTenantMembershipRolesUseCase {

  ReplaceTenantMembershipRolesResult replaceRoles(ReplaceTenantMembershipRolesCommand command);
}
