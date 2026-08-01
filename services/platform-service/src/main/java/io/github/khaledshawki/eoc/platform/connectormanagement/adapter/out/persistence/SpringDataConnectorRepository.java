package io.github.khaledshawki.eoc.platform.connectormanagement.adapter.out.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataConnectorRepository extends JpaRepository<ConnectorJpaEntity, UUID> {

  Optional<ConnectorJpaEntity> findByIdAndTenantId(UUID id, UUID tenantId);

  List<ConnectorJpaEntity> findAllByTenantIdOrderByNameAscIdAsc(UUID tenantId);

  boolean existsByTenantIdAndName(UUID tenantId, String name);
}
