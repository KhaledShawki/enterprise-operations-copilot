package io.github.khaledshawki.eoc.platform.connectormanagement.adapter.in.messaging.kafka;

final class RetryableConnectorKafkaConsumptionException extends ConnectorKafkaConsumptionException {

  RetryableConnectorKafkaConsumptionException(String failureCode, Throwable cause) {
    super(failureCode, cause);
  }

  @Override
  boolean retryable() {
    return true;
  }
}
