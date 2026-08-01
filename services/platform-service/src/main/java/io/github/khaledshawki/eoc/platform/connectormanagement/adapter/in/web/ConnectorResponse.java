package io.github.khaledshawki.eoc.platform.connectormanagement.adapter.in.web;

import io.github.khaledshawki.eoc.connectormanagement.application.port.in.ConnectorResult;
import java.util.Objects;
import java.util.UUID;

public record ConnectorResponse(
    UUID id,
    UUID tenantId,
    String name,
    String type,
    String status,
    String endpoint,
    UUID credentialReference,
    SyncPolicyResponse syncPolicy,
    String health) {

  static ConnectorResponse from(ConnectorResult result) {
    Objects.requireNonNull(result, "Connector result cannot be null");

    return new ConnectorResponse(
        result.connectorId().value(),
        result.tenantId().value(),
        result.name().value(),
        result.type().value(),
        result.status().name(),
        result.endpoint().value().toString(),
        result.credentialReference().value(),
        new SyncPolicyResponse(
            result.syncPolicy().mode().name(), result.syncPolicy().interval().toString()),
        result.health().name());
  }

  public record SyncPolicyResponse(String mode, String interval) {}
}
