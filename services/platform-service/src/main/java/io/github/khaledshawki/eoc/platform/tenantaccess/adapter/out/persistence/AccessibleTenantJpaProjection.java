package io.github.khaledshawki.eoc.platform.tenantaccess.adapter.out.persistence;

import io.github.khaledshawki.eoc.tenantaccess.domain.model.TenantMembershipStatus;
import io.github.khaledshawki.eoc.tenantaccess.domain.model.TenantStatus;
import java.util.UUID;

interface AccessibleTenantJpaProjection {

  UUID getMembershipId();

  UUID getTenantId();

  String getTenantKey();

  String getDisplayName();

  TenantStatus getTenantStatus();

  TenantMembershipStatus getMembershipStatus();
}
