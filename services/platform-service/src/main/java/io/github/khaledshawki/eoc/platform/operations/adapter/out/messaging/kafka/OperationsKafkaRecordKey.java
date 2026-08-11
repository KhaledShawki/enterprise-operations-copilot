package io.github.khaledshawki.eoc.platform.operations.adapter.out.messaging.kafka;

import io.github.khaledshawki.eoc.operations.application.model.event.OperationsIntegrationEventEnvelope;
import java.util.Objects;

public final class OperationsKafkaRecordKey {

  private OperationsKafkaRecordKey() {}

  public static String from(OperationsIntegrationEventEnvelope event) {
    OperationsIntegrationEventEnvelope requiredEvent =
        Objects.requireNonNull(event, "Operations integration event cannot be null");
    return requiredEvent.tenantId()
        + ":"
        + requiredEvent.aggregateType()
        + ":"
        + requiredEvent.aggregateId();
  }
}
