package io.github.khaledshawki.eoc.connectormanagement.application.port.in;

import io.github.khaledshawki.eoc.connectormanagement.application.model.authorization.ConnectorActor;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.SyncPolicy;
import java.time.Duration;
import java.util.Objects;
import java.util.UUID;

public record CreateConnectorCommand(
    ConnectorActor actor,
    UUID tenantId,
    String name,
    String type,
    String endpoint,
    UUID credentialReference,
    SyncPolicy.Mode syncMode,
    Duration syncInterval) {

  public CreateConnectorCommand {
    Objects.requireNonNull(actor, "Connector actor cannot be null");
    Objects.requireNonNull(tenantId, "Tenant id cannot be null");
    Objects.requireNonNull(name, "Connector name cannot be null");
    Objects.requireNonNull(type, "Connector type cannot be null");
    Objects.requireNonNull(endpoint, "Connector endpoint cannot be null");
    Objects.requireNonNull(credentialReference, "Credential reference cannot be null");
    Objects.requireNonNull(syncMode, "Sync mode cannot be null");
    Objects.requireNonNull(syncInterval, "Sync interval cannot be null");
  }
}
