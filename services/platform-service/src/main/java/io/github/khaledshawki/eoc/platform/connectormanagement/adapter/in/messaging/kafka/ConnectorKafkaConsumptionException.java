package io.github.khaledshawki.eoc.platform.connectormanagement.adapter.in.messaging.kafka;

import java.util.Objects;

abstract sealed class ConnectorKafkaConsumptionException extends RuntimeException
    permits RetryableConnectorKafkaConsumptionException,
        TerminalConnectorKafkaConsumptionException {

  private final String failureCode;

  ConnectorKafkaConsumptionException(String failureCode, Throwable cause) {
    super("Connector Kafka record consumption failed", cause);
    this.failureCode = Objects.requireNonNull(failureCode, "Kafka failure code cannot be null");
  }

  final String failureCode() {
    return failureCode;
  }

  abstract boolean retryable();
}
