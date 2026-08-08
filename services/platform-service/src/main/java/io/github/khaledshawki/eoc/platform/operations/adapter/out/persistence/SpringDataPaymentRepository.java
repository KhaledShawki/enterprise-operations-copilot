package io.github.khaledshawki.eoc.platform.operations.adapter.out.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

interface SpringDataPaymentRepository
    extends JpaRepository<PaymentJpaEntity, UUID>, JpaSpecificationExecutor<PaymentJpaEntity> {

  Optional<PaymentJpaEntity> findByIdAndTenantId(UUID id, UUID tenantId);
}
