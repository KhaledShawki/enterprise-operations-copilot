package io.github.khaledshawki.eoc.platform.operations.adapter.out.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataReceivableSettlementRepository
    extends JpaRepository<ReceivableSettlementJpaEntity, UUID> {

  Optional<ReceivableSettlementJpaEntity> findByIdAndTenantId(UUID id, UUID tenantId);

  Optional<ReceivableSettlementJpaEntity> findByTenantIdAndPaymentId(UUID tenantId, UUID paymentId);
}
