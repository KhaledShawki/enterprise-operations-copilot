package io.github.khaledshawki.eoc.platform.connectormanagement.adapter.out.persistence;

import io.github.khaledshawki.eoc.connectormanagement.domain.model.Connector;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ConnectorEndpoint;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ConnectorId;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ConnectorName;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ConnectorTenantId;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ConnectorType;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.CredentialReference;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.SyncPolicy;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

final class ConnectorPersistenceMapper {

  ConnectorJpaEntity toEntity(Connector connector, Instant now) {
    Objects.requireNonNull(connector, "Connector cannot be null");
    Objects.requireNonNull(now, "Timestamp cannot be null");

    return new ConnectorJpaEntity(
        connector.id().value(),
        connector.tenantId().value(),
        connector.name().value(),
        connector.type().value(),
        connector.status(),
        connector.endpoint().value().toString(),
        connector.credentialReference().value(),
        connector.syncPolicy().mode(),
        connector.syncPolicy().interval().toString(),
        connector.health(),
        now,
        now);
  }

  Connector toDomain(ConnectorJpaEntity entity) {
    Objects.requireNonNull(entity, "Entity cannot be null");

    return Connector.reconstitute(
        ConnectorId.of(entity.getId()),
        ConnectorTenantId.of(entity.getTenantId()),
        ConnectorName.of(entity.getName()),
        ConnectorType.of(entity.getConnectorType()),
        entity.getStatus(),
        ConnectorEndpoint.of(entity.getEndpoint()),
        CredentialReference.of(entity.getCredentialReference()),
        new SyncPolicy(entity.getSyncMode(), Duration.parse(entity.getSyncInterval())),
        entity.getHealth());
  }

  ConnectorJpaEntity updateEntity(Connector connector, ConnectorJpaEntity entity, Instant now) {
    Objects.requireNonNull(connector, "Connector cannot be null");
    Objects.requireNonNull(entity, "Entity cannot be null");
    Objects.requireNonNull(now, "Timestamp cannot be null");

    ensureSameImmutableState(connector, entity);

    entity.updateMutableState(
        connector.name().value(),
        connector.status(),
        connector.endpoint().value().toString(),
        connector.credentialReference().value(),
        connector.syncPolicy().mode(),
        connector.syncPolicy().interval().toString(),
        connector.health(),
        now);

    return entity;
  }

  private void ensureSameImmutableState(Connector connector, ConnectorJpaEntity entity) {
    if (!entity.getId().equals(connector.id().value())) {
      throw new IllegalArgumentException("Connector id mismatch");
    }
    if (!entity.getTenantId().equals(connector.tenantId().value())) {
      throw new IllegalArgumentException("Connector tenant id mismatch");
    }
    if (!entity.getConnectorType().equals(connector.type().value())) {
      throw new IllegalArgumentException("Connector type mismatch");
    }
  }
}
