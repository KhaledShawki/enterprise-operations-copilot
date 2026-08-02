package io.github.khaledshawki.eoc.platform.operations.adapter.out.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataBusinessPartnerRepository
    extends JpaRepository<BusinessPartnerJpaEntity, UUID> {

  Optional<BusinessPartnerJpaEntity> findByIdAndTenantId(UUID id, UUID tenantId);
}
