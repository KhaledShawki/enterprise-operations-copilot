package io.github.khaledshawki.eoc.platform.operations.adapter.out.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataReceivableAllocationRepository
    extends JpaRepository<ReceivableAllocationJpaEntity, ReceivableAllocationJpaId> {

  Optional<ReceivableAllocationJpaEntity> findByIdAndTenantId(UUID id, UUID tenantId);

  List<ReceivableAllocationJpaEntity> findAllByTenantIdAndSettlementIdOrderByAllocationPositionAsc(
      UUID tenantId, UUID settlementId);
}
