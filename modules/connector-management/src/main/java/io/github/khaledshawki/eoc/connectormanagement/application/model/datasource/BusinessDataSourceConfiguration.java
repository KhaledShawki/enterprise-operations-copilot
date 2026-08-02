package io.github.khaledshawki.eoc.connectormanagement.application.model.datasource;

import io.github.khaledshawki.eoc.connectormanagement.domain.model.Connector;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ConnectorEndpoint;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ConnectorId;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ConnectorTenantId;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ConnectorType;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.CredentialReference;
import java.util.Objects;

/** Immutable connector snapshot supplied to a business data source adapter. */
public record BusinessDataSourceConfiguration(
    ConnectorId connectorId,
    ConnectorTenantId tenantId,
    ConnectorType connectorType,
    ConnectorEndpoint endpoint,
    CredentialReference credentialReference) {

  public BusinessDataSourceConfiguration {
    Objects.requireNonNull(connectorId, "Connector id cannot be null");
    Objects.requireNonNull(tenantId, "Connector tenant id cannot be null");
    Objects.requireNonNull(connectorType, "Connector type cannot be null");
    Objects.requireNonNull(endpoint, "Connector endpoint cannot be null");
    Objects.requireNonNull(credentialReference, "Credential reference cannot be null");
  }

  public static BusinessDataSourceConfiguration from(Connector connector) {
    Objects.requireNonNull(connector, "Connector cannot be null");
    return new BusinessDataSourceConfiguration(
        connector.id(),
        connector.tenantId(),
        connector.type(),
        connector.endpoint(),
        connector.credentialReference());
  }
}
