package io.github.khaledshawki.eoc.connectormanagement.domain.model;

import java.util.Objects;
import java.util.UUID;

public record ConnectorId(UUID value) {

  public ConnectorId {
    Objects.requireNonNull(value, "Connector id cannot be null");
  }

  public static ConnectorId of(UUID value) {
    return new ConnectorId(value);
  }

  public static ConnectorId generate() {
    return new ConnectorId(UUID.randomUUID());
  }
}
