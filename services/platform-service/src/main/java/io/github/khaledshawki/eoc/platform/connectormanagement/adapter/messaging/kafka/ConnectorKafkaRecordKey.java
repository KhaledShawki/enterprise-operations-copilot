package io.github.khaledshawki.eoc.platform.connectormanagement.adapter.messaging.kafka;

import io.github.khaledshawki.eoc.connectormanagement.application.model.event.ConnectorIntegrationEventEnvelope;
import java.util.Objects;

public final class ConnectorKafkaRecordKey {

  private ConnectorKafkaRecordKey() {}

  public static String from(ConnectorIntegrationEventEnvelope event) {
    ConnectorIntegrationEventEnvelope requiredEvent =
        Objects.requireNonNull(event, "Connector integration event cannot be null");
    return requiredEvent.tenantId()
        + ":"
        + requiredEvent.aggregateType()
        + ":"
        + requiredEvent.aggregateId();
  }
}
