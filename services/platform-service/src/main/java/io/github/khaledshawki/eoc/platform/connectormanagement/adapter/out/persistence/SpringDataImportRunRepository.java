package io.github.khaledshawki.eoc.platform.connectormanagement.adapter.out.persistence;

import io.github.khaledshawki.eoc.connectormanagement.domain.model.ImportStatus;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ImportType;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataImportRunRepository extends JpaRepository<ImportRunJpaEntity, UUID> {

  Optional<ImportRunJpaEntity> findByIdAndTenantId(UUID id, UUID tenantId);

  boolean existsByTenantIdAndConnectorIdAndImportTypeAndStatusIn(
      UUID tenantId, UUID connectorId, ImportType importType, Collection<ImportStatus> statuses);
}
