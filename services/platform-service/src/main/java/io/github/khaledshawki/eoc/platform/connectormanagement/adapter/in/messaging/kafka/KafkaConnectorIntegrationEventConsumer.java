package io.github.khaledshawki.eoc.platform.connectormanagement.adapter.in.messaging.kafka;

import io.github.khaledshawki.eoc.connectormanagement.application.exception.ConnectorEventConsumptionException;
import io.github.khaledshawki.eoc.connectormanagement.application.model.event.ConnectorIntegrationEventEnvelope;
import io.github.khaledshawki.eoc.connectormanagement.application.port.in.ConsumeConnectorIntegrationEventUseCase;
import io.github.khaledshawki.eoc.platform.connectormanagement.adapter.messaging.kafka.ConnectorKafkaRecordKey;
import java.util.Objects;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;

final class KafkaConnectorIntegrationEventConsumer {

  static final String KEY_MISMATCH = "kafka-event-key-mismatch";
  static final String CONSUMPTION_FAILED = "kafka-event-consumption-failed";

  private final KafkaConnectorIntegrationEventDecoder decoder;
  private final ConsumeConnectorIntegrationEventUseCase useCase;

  KafkaConnectorIntegrationEventConsumer(
      KafkaConnectorIntegrationEventDecoder decoder,
      ConsumeConnectorIntegrationEventUseCase useCase) {
    this.decoder = Objects.requireNonNull(decoder, "Kafka event decoder cannot be null");
    this.useCase = Objects.requireNonNull(useCase, "Connector event use case cannot be null");
  }

  @KafkaListener(
      id = "connector-integration-event-consumer",
      topics = "${eoc.connector-events.kafka.topic}",
      groupId = "${eoc.connector-events.kafka.consumer.group-id}",
      containerFactory = "connectorKafkaListenerContainerFactory")
  void consume(ConsumerRecord<String, String> record) {
    Objects.requireNonNull(record, "Kafka consumer record cannot be null");
    ConnectorIntegrationEventEnvelope event = decoder.decode(record.value());
    if (!ConnectorKafkaRecordKey.from(event).equals(record.key())) {
      throw new TerminalConnectorKafkaConsumptionException(KEY_MISMATCH, null);
    }

    try {
      useCase.consume(event);
    } catch (ConnectorKafkaConsumptionException exception) {
      throw exception;
    } catch (ConnectorEventConsumptionException exception) {
      if (exception.retryable()) {
        throw new RetryableConnectorKafkaConsumptionException(exception.failureCode(), exception);
      }
      throw new TerminalConnectorKafkaConsumptionException(exception.failureCode(), exception);
    } catch (RuntimeException exception) {
      throw new RetryableConnectorKafkaConsumptionException(CONSUMPTION_FAILED, exception);
    }
  }
}
