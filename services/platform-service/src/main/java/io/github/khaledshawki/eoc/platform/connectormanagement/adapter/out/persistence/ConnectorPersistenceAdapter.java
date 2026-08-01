package io.github.khaledshawki.eoc.platform.connectormanagement.adapter.out.persistence;

import io.github.khaledshawki.eoc.connectormanagement.application.exception.ConnectorNameAlreadyExistsException;
import io.github.khaledshawki.eoc.connectormanagement.application.port.out.ConnectorRepository;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.Connector;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ConnectorId;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ConnectorName;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ConnectorTenantId;
import io.github.khaledshawki.eoc.platform.persistence.PersistenceConstraintViolationDetector;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
class ConnectorPersistenceAdapter implements ConnectorRepository {

  private static final String CONNECTOR_TENANT_NAME_UNIQUE_CONSTRAINT = "uk_connectors_tenant_name";

  private final SpringDataConnectorRepository connectorRepository;
  private final ConnectorPersistenceMapper connectorPersistenceMapper;
  private final Clock clock;

  ConnectorPersistenceAdapter(
      SpringDataConnectorRepository connectorRepository,
      ConnectorPersistenceMapper connectorPersistenceMapper,
      Clock clock) {
    this.connectorRepository =
        Objects.requireNonNull(connectorRepository, "Connector repository cannot be null");
    this.connectorPersistenceMapper =
        Objects.requireNonNull(
            connectorPersistenceMapper, "Connector persistence mapper cannot be null");
    this.clock = Objects.requireNonNull(clock, "Clock cannot be null");
  }

  @Override
  @Transactional
  public Connector save(Connector connector) {
    Objects.requireNonNull(connector, "Connector cannot be null");

    Instant now = clock.instant();
    ConnectorJpaEntity entity =
        connectorRepository
            .findByIdAndTenantId(connector.id().value(), connector.tenantId().value())
            .map(existing -> connectorPersistenceMapper.updateEntity(connector, existing, now))
            .orElseGet(() -> connectorPersistenceMapper.toEntity(connector, now));

    try {
      return connectorPersistenceMapper.toDomain(connectorRepository.saveAndFlush(entity));
    } catch (DataIntegrityViolationException exception) {
      if (PersistenceConstraintViolationDetector.hasConstraintName(
          exception, CONNECTOR_TENANT_NAME_UNIQUE_CONSTRAINT)) {
        throw new ConnectorNameAlreadyExistsException(
            connector.tenantId(), connector.name(), exception);
      }
      throw exception;
    }
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<Connector> findById(ConnectorTenantId tenantId, ConnectorId connectorId) {
    Objects.requireNonNull(tenantId, "Connector tenant id cannot be null");
    Objects.requireNonNull(connectorId, "Connector id cannot be null");

    return connectorRepository
        .findByIdAndTenantId(connectorId.value(), tenantId.value())
        .map(connectorPersistenceMapper::toDomain);
  }

  @Override
  @Transactional(readOnly = true)
  public boolean existsByTenantIdAndName(ConnectorTenantId tenantId, ConnectorName connectorName) {
    Objects.requireNonNull(tenantId, "Connector tenant id cannot be null");
    Objects.requireNonNull(connectorName, "Connector name cannot be null");

    return connectorRepository.existsByTenantIdAndName(tenantId.value(), connectorName.value());
  }
}
