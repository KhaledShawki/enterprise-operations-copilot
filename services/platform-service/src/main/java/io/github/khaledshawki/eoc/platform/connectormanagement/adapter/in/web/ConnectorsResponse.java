package io.github.khaledshawki.eoc.platform.connectormanagement.adapter.in.web;

import io.github.khaledshawki.eoc.connectormanagement.application.port.in.ListConnectorsResult;
import java.util.List;
import java.util.Objects;

public record ConnectorsResponse(List<ConnectorResponse> connectors) {

  public ConnectorsResponse {
    Objects.requireNonNull(connectors, "Connectors cannot be null");
    connectors = List.copyOf(connectors);
  }

  static ConnectorsResponse from(ListConnectorsResult result) {
    Objects.requireNonNull(result, "List connectors result cannot be null");

    return new ConnectorsResponse(
        result.connectors().stream().map(ConnectorResponse::from).toList());
  }
}
