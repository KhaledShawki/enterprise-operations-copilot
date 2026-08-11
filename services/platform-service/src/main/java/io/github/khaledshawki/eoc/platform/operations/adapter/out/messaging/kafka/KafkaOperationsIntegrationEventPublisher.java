package io.github.khaledshawki.eoc.platform.operations.adapter.out.messaging.kafka;

import io.github.khaledshawki.eoc.operations.application.exception.OperationsEventPublicationException;
import io.github.khaledshawki.eoc.operations.application.model.event.BusinessPartnerSynchronizedPayload;
import io.github.khaledshawki.eoc.operations.application.model.event.InvoiceSynchronizedPayload;
import io.github.khaledshawki.eoc.operations.application.model.event.OperationsIntegrationEvent;
import io.github.khaledshawki.eoc.operations.application.model.event.OperationsIntegrationEventEnvelope;
import io.github.khaledshawki.eoc.operations.application.model.event.OperationsIntegrationEventPayload;
import io.github.khaledshawki.eoc.operations.application.model.event.OperationsIntegrationEventType;
import io.github.khaledshawki.eoc.operations.application.model.event.PaymentSynchronizedPayload;
import io.github.khaledshawki.eoc.operations.application.model.event.ReceivableAllocationAppliedPayload;
import io.github.khaledshawki.eoc.operations.application.model.event.ReceivableAllocationReversedPayload;
import io.github.khaledshawki.eoc.operations.application.port.out.OperationsIntegrationEventPublisher;
import io.github.khaledshawki.eoc.platform.operations.configuration.OperationsKafkaProperties;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

@Component
@ConditionalOnProperty(name = "eoc.operations-events.transport", havingValue = "kafka")
final class KafkaOperationsIntegrationEventPublisher
    implements OperationsIntegrationEventPublisher {

  private static final String CONTRACT_REJECTED = "kafka-event-contract-rejected";
  private static final String SERIALIZATION_FAILED = "kafka-event-serialization-failed";
  private static final String PUBLISH_TIMEOUT = "kafka-publish-timeout";
  private static final String PUBLISH_INTERRUPTED = "kafka-publish-interrupted";
  private static final String PUBLISH_CANCELLED = "kafka-publish-cancelled";
  private static final String PUBLISH_FAILED =
      OperationsKafkaPublicationFailureClassifier.PUBLISH_FAILED;

  private final KafkaTemplate<String, String> kafkaTemplate;
  private final JsonMapper jsonMapper;
  private final String topic;
  private final Duration sendTimeout;

  KafkaOperationsIntegrationEventPublisher(
      KafkaTemplate<String, String> kafkaTemplate,
      JsonMapper jsonMapper,
      OperationsKafkaProperties properties) {
    this.kafkaTemplate = Objects.requireNonNull(kafkaTemplate, "Kafka template cannot be null");
    this.jsonMapper = Objects.requireNonNull(jsonMapper, "JSON mapper cannot be null");
    OperationsKafkaProperties requiredProperties =
        Objects.requireNonNull(properties, "Operations Kafka properties cannot be null");
    this.topic = requiredProperties.topic();
    this.sendTimeout = requiredProperties.sendTimeout();
  }

  @Override
  public void publish(OperationsIntegrationEventEnvelope event) {
    Objects.requireNonNull(event, "Operations integration event cannot be null");
    ProducerRecord<String, String> record =
        new ProducerRecord<>(
            topic,
            null,
            event.occurredAt().toEpochMilli(),
            OperationsKafkaRecordKey.from(event),
            serialize(event));

    CompletableFuture<SendResult<String, String>> send;
    try {
      send = kafkaTemplate.send(record);
    } catch (RuntimeException exception) {
      throw OperationsKafkaPublicationFailureClassifier.classify(exception);
    }
    if (send == null) {
      throw new OperationsEventPublicationException(
          PUBLISH_FAILED, false, new IllegalStateException("KafkaTemplate returned a null future"));
    }

    try {
      send.get(sendTimeout.toMillis(), TimeUnit.MILLISECONDS);
    } catch (TimeoutException exception) {
      throw new OperationsEventPublicationException(PUBLISH_TIMEOUT, true, exception);
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new OperationsEventPublicationException(PUBLISH_INTERRUPTED, true, exception);
    } catch (CancellationException exception) {
      throw new OperationsEventPublicationException(PUBLISH_CANCELLED, true, exception);
    } catch (ExecutionException exception) {
      throw OperationsKafkaPublicationFailureClassifier.classify(
          exception.getCause() == null ? exception : exception.getCause());
    }
  }

  private String serialize(OperationsIntegrationEventEnvelope event) {
    OperationsIntegrationEventPayload payload = requireValidPayload(event);
    try {
      Map<String, Object> wireEnvelope = new LinkedHashMap<>();
      wireEnvelope.put("eventId", event.eventId().toString());
      wireEnvelope.put("eventType", event.eventType());
      wireEnvelope.put("schemaVersion", event.schemaVersion());
      wireEnvelope.put("tenantId", event.tenantId().toString());
      wireEnvelope.put("aggregateType", event.aggregateType());
      wireEnvelope.put("aggregateId", event.aggregateId().toString());
      wireEnvelope.put("aggregateVersion", event.aggregateVersion());
      wireEnvelope.put("payload", payload);
      wireEnvelope.put("occurredAt", event.occurredAt().toString());
      return jsonMapper.writeValueAsString(wireEnvelope);
    } catch (JacksonException exception) {
      throw new OperationsEventPublicationException(SERIALIZATION_FAILED, false, exception);
    }
  }

  private OperationsIntegrationEventPayload requireValidPayload(
      OperationsIntegrationEventEnvelope event) {
    OperationsIntegrationEventType eventType = requireSupportedContract(event);
    try {
      OperationsIntegrationEventPayload payload =
          switch (eventType) {
            case BUSINESS_PARTNER_SYNCHRONIZED ->
                jsonMapper.readValue(event.payload(), BusinessPartnerSynchronizedPayload.class);
            case INVOICE_SYNCHRONIZED ->
                jsonMapper.readValue(event.payload(), InvoiceSynchronizedPayload.class);
            case PAYMENT_SYNCHRONIZED ->
                jsonMapper.readValue(event.payload(), PaymentSynchronizedPayload.class);
            case RECEIVABLE_ALLOCATION_APPLIED ->
                jsonMapper.readValue(event.payload(), ReceivableAllocationAppliedPayload.class);
            case RECEIVABLE_ALLOCATION_REVERSED ->
                jsonMapper.readValue(event.payload(), ReceivableAllocationReversedPayload.class);
          };
      new OperationsIntegrationEvent(
          event.eventId(),
          eventType,
          event.tenantId(),
          event.aggregateType(),
          event.aggregateId(),
          event.aggregateVersion(),
          event.occurredAt(),
          payload);
      return payload;
    } catch (JacksonException | IllegalArgumentException exception) {
      throw new OperationsEventPublicationException(CONTRACT_REJECTED, false, exception);
    }
  }

  private static OperationsIntegrationEventType requireSupportedContract(
      OperationsIntegrationEventEnvelope event) {
    for (OperationsIntegrationEventType type : OperationsIntegrationEventType.values()) {
      if (type.eventType().equals(event.eventType())
          && type.schemaVersion() == event.schemaVersion()
          && type.aggregateType().equals(event.aggregateType())) {
        return type;
      }
    }
    throw new OperationsEventPublicationException(CONTRACT_REJECTED, false, null);
  }
}
