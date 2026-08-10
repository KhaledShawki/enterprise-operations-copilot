package io.github.khaledshawki.eoc.platform.connectormanagement.adapter.in.messaging.kafka;

final class TerminalConnectorKafkaConsumptionException extends ConnectorKafkaConsumptionException {

  TerminalConnectorKafkaConsumptionException(String failureCode, Throwable cause) {
    super(failureCode, cause);
  }

  @Override
  boolean retryable() {
    return false;
  }
}
