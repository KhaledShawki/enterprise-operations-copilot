package io.github.khaledshawki.eoc.platform.tenantaccess.adapter.out.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface SpringDataTenantMembershipRepository
    extends JpaRepository<TenantMembershipJpaEntity, UUID> {

  Optional<TenantMembershipJpaEntity> findByTenantIdAndPlatformUserId(
      UUID tenantId, UUID platformUserId);

  boolean existsByTenantIdAndPlatformUserId(UUID tenantId, UUID platformUserId);

  @Query(
      """
      select
        membership.id as membershipId,
        tenant.id as tenantId,
        tenant.tenantKey as tenantKey,
        tenant.displayName as displayName,
        tenant.status as tenantStatus,
        membership.status as membershipStatus
      from TenantMembershipJpaEntity membership
      join TenantJpaEntity tenant
        on tenant.id = membership.tenantId
      where membership.platformUserId = :platformUserId
      order by tenant.tenantKey
      """)
  List<AccessibleTenantJpaProjection> findAccessibleTenantsByPlatformUserId(
      @Param("platformUserId") UUID platformUserId);
}
