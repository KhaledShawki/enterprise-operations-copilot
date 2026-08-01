package io.github.khaledshawki.eoc.platform.connectormanagement.adapter.out.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataConnectorRepository extends JpaRepository<ConnectorJpaEntity, UUID> {

  Optional<ConnectorJpaEntity> findByIdAndTenantId(UUID id, UUID tenantId);

  boolean existsByTenantIdAndName(UUID tenantId, String name);
}
