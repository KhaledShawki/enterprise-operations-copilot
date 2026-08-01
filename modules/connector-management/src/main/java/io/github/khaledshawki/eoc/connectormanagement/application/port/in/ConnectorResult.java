package io.github.khaledshawki.eoc.connectormanagement.application.port.in;

import io.github.khaledshawki.eoc.connectormanagement.domain.model.Connector;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ConnectorEndpoint;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ConnectorHealth;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ConnectorId;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ConnectorName;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ConnectorStatus;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ConnectorTenantId;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ConnectorType;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.CredentialReference;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.SyncPolicy;
import java.util.Objects;

public record ConnectorResult(
    ConnectorId connectorId,
    ConnectorTenantId tenantId,
    ConnectorName name,
    ConnectorType type,
    ConnectorStatus status,
    ConnectorEndpoint endpoint,
    CredentialReference credentialReference,
    SyncPolicy syncPolicy,
    ConnectorHealth health) {

  public ConnectorResult {
    Objects.requireNonNull(connectorId, "Connector id cannot be null");
    Objects.requireNonNull(tenantId, "Connector tenant id cannot be null");
    Objects.requireNonNull(name, "Connector name cannot be null");
    Objects.requireNonNull(type, "Connector type cannot be null");
    Objects.requireNonNull(status, "Connector status cannot be null");
    Objects.requireNonNull(endpoint, "Connector endpoint cannot be null");
    Objects.requireNonNull(credentialReference, "Credential reference cannot be null");
    Objects.requireNonNull(syncPolicy, "Sync policy cannot be null");
    Objects.requireNonNull(health, "Connector health cannot be null");
  }

  public static ConnectorResult from(Connector connector) {
    Objects.requireNonNull(connector, "Connector cannot be null");

    return new ConnectorResult(
        connector.id(),
        connector.tenantId(),
        connector.name(),
        connector.type(),
        connector.status(),
        connector.endpoint(),
        connector.credentialReference(),
        connector.syncPolicy(),
        connector.health());
  }
}
