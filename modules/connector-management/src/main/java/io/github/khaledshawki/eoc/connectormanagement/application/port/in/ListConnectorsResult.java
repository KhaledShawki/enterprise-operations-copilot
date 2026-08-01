package io.github.khaledshawki.eoc.connectormanagement.application.port.in;

import java.util.List;
import java.util.Objects;

public record ListConnectorsResult(List<ConnectorResult> connectors) {

  public ListConnectorsResult {
    Objects.requireNonNull(connectors, "Connectors cannot be null");

    if (connectors.stream().anyMatch(Objects::isNull)) {
      throw new IllegalArgumentException("Connectors cannot contain null values");
    }

    connectors = List.copyOf(connectors);
  }
}
