package io.github.khaledshawki.eoc.connectormanagement.domain.model;

import java.util.Objects;
import java.util.UUID;

public record ConnectorTenantId(UUID value) {

  public ConnectorTenantId {
    Objects.requireNonNull(value, "Connector tenant id cannot be null");
  }

  public static ConnectorTenantId of(UUID value) {
    return new ConnectorTenantId(value);
  }
}
