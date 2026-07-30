package io.github.khaledshawki.eoc.tenantaccess.application.port.out;

import io.github.khaledshawki.eoc.tenantaccess.domain.model.TenantMembership;

public interface TenantMembershipRoleWriteRepository {

  TenantMembership replaceRoles(TenantMembership membership);
}
