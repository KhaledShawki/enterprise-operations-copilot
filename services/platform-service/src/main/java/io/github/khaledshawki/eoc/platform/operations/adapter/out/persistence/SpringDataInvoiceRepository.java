package io.github.khaledshawki.eoc.platform.operations.adapter.out.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

interface SpringDataInvoiceRepository
    extends JpaRepository<InvoiceJpaEntity, UUID>, JpaSpecificationExecutor<InvoiceJpaEntity> {

  Optional<InvoiceJpaEntity> findByIdAndTenantId(UUID id, UUID tenantId);
}
