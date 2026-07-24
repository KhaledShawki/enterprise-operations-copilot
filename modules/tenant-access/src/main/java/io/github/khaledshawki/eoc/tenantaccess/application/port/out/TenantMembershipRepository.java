package io.github.khaledshawki.eoc.tenantaccess.application.port.out;

import io.github.khaledshawki.eoc.tenantaccess.domain.model.PlatformUserId;
import io.github.khaledshawki.eoc.tenantaccess.domain.model.TenantId;
import io.github.khaledshawki.eoc.tenantaccess.domain.model.TenantMembership;
import io.github.khaledshawki.eoc.tenantaccess.domain.model.TenantMembershipId;
import java.util.Optional;

public interface TenantMembershipRepository {

  TenantMembership save(TenantMembership membership);

  Optional<TenantMembership> findById(TenantMembershipId membershipId);

  Optional<TenantMembership> findByTenantIdAndUserId(TenantId tenantId, PlatformUserId userId);

  boolean existsByTenantIdAndUserId(TenantId tenantId, PlatformUserId userId);
}
