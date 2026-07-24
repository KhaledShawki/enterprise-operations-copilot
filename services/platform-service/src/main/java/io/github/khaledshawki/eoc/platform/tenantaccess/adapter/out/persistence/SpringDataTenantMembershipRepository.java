package io.github.khaledshawki.eoc.platform.tenantaccess.adapter.out.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataTenantMembershipRepository
    extends JpaRepository<TenantMembershipJpaEntity, UUID> {

  Optional<TenantMembershipJpaEntity> findByTenantIdAndPlatformUserId(
      UUID tenantId, UUID platformUserId);

  boolean existsByTenantIdAndPlatformUserId(UUID tenantId, UUID platformUserId);
}
