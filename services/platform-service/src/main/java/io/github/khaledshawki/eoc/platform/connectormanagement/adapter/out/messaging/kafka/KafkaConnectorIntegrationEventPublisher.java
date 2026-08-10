package io.github.khaledshawki.eoc.platform.connectormanagement.adapter.out.messaging.kafka;

import io.github.khaledshawki.eoc.connectormanagement.application.exception.ConnectorEventPublicationException;
import io.github.khaledshawki.eoc.connectormanagement.application.model.event.ConnectorIntegrationEvent;
import io.github.khaledshawki.eoc.connectormanagement.application.model.event.ConnectorIntegrationEventEnvelope;
import io.github.khaledshawki.eoc.connectormanagement.application.model.event.ConnectorIntegrationEventPayload;
import io.github.khaledshawki.eoc.connectormanagement.application.model.event.ConnectorIntegrationEventType;
import io.github.khaledshawki.eoc.connectormanagement.application.model.event.ImportRunCompletedPayload;
import io.github.khaledshawki.eoc.connectormanagement.application.model.event.ImportRunFailedPayload;
import io.github.khaledshawki.eoc.connectormanagement.application.model.event.ImportRunRetryScheduledPayload;
import io.github.khaledshawki.eoc.connectormanagement.application.port.out.ConnectorIntegrationEventPublisher;
import io.github.khaledshawki.eoc.platform.connectormanagement.adapter.messaging.kafka.ConnectorKafkaRecordKey;
import io.github.khaledshawki.eoc.platform.connectormanagement.configuration.ConnectorKafkaProperties;
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
import org.apache.kafka.common.KafkaException;
import org.apache.kafka.common.errors.AuthenticationException;
import org.apache.kafka.common.errors.AuthorizationException;
import org.apache.kafka.common.errors.InvalidTopicException;
import org.apache.kafka.common.errors.RecordTooLargeException;
import org.apache.kafka.common.errors.RetriableException;
import org.apache.kafka.common.errors.SerializationException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

@Component
@ConditionalOnProperty(name = "eoc.connector-events.transport", havingValue = "kafka")
final class KafkaConnectorIntegrationEventPublisher implements ConnectorIntegrationEventPublisher {

  private static final String CONTRACT_REJECTED = "kafka-event-contract-rejected";
  private static final String SERIALIZATION_FAILED = "kafka-event-serialization-failed";
  private static final String PUBLISH_TIMEOUT = "kafka-publish-timeout";
  private static final String PUBLISH_INTERRUPTED = "kafka-publish-interrupted";
  private static final String PUBLISH_CANCELLED = "kafka-publish-cancelled";
  private static final String BROKER_RETRYABLE = "kafka-broker-retryable-error";
  private static final String PUBLISH_REJECTED = "kafka-publish-rejected";
  private static final String PUBLISH_FAILED = "kafka-publish-failed";

  private final KafkaTemplate<String, String> kafkaTemplate;
  private final JsonMapper jsonMapper;
  private final String topic;
  private final Duration sendTimeout;

  KafkaConnectorIntegrationEventPublisher(
      KafkaTemplate<String, String> kafkaTemplate,
      JsonMapper jsonMapper,
      ConnectorKafkaProperties properties) {
    this.kafkaTemplate = Objects.requireNonNull(kafkaTemplate, "Kafka template cannot be null");
    this.jsonMapper = Objects.requireNonNull(jsonMapper, "JSON mapper cannot be null");
    ConnectorKafkaProperties requiredProperties =
        Objects.requireNonNull(properties, "Connector Kafka properties cannot be null");
    this.topic = requiredProperties.topic();
    this.sendTimeout = requiredProperties.sendTimeout();
  }

  @Override
  public void publish(ConnectorIntegrationEventEnvelope event) {
    Objects.requireNonNull(event, "Connector integration event cannot be null");
    ProducerRecord<String, String> record =
        new ProducerRecord<>(
            topic,
            null,
            event.occurredAt().toEpochMilli(),
            ConnectorKafkaRecordKey.from(event),
            serialize(event));

    CompletableFuture<SendResult<String, String>> send;
    try {
      send = kafkaTemplate.send(record);
    } catch (RuntimeException exception) {
      throw classify(exception);
    }
    if (send == null) {
      throw new ConnectorEventPublicationException(
          PUBLISH_FAILED, false, new IllegalStateException("KafkaTemplate returned a null future"));
    }

    try {
      send.get(sendTimeout.toMillis(), TimeUnit.MILLISECONDS);
    } catch (TimeoutException exception) {
      throw new ConnectorEventPublicationException(PUBLISH_TIMEOUT, true, exception);
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new ConnectorEventPublicationException(PUBLISH_INTERRUPTED, true, exception);
    } catch (CancellationException exception) {
      throw new ConnectorEventPublicationException(PUBLISH_CANCELLED, true, exception);
    } catch (ExecutionException exception) {
      throw classify(exception.getCause() == null ? exception : exception.getCause());
    }
  }

  private String serialize(ConnectorIntegrationEventEnvelope event) {
    ConnectorIntegrationEventPayload payload = requireValidPayload(event);
    try {
      Map<String, Object> wireEnvelope = new LinkedHashMap<>();
      wireEnvelope.put("eventId", event.eventId().toString());
      wireEnvelope.put("eventType", event.eventType());
      wireEnvelope.put("schemaVersion", event.schemaVersion());
      wireEnvelope.put("tenantId", event.tenantId().toString());
      wireEnvelope.put("aggregateType", event.aggregateType());
      wireEnvelope.put("aggregateId", event.aggregateId().toString());
      wireEnvelope.put("payload", payload);
      wireEnvelope.put("occurredAt", event.occurredAt().toString());
      return jsonMapper.writeValueAsString(wireEnvelope);
    } catch (JacksonException exception) {
      throw new ConnectorEventPublicationException(SERIALIZATION_FAILED, false, exception);
    }
  }

  private ConnectorIntegrationEventPayload requireValidPayload(
      ConnectorIntegrationEventEnvelope event) {
    ConnectorIntegrationEventType eventType = requireSupportedContract(event);
    try {
      ConnectorIntegrationEventPayload payload =
          switch (eventType) {
            case IMPORT_RUN_COMPLETED ->
                jsonMapper.readValue(event.payload(), ImportRunCompletedPayload.class);
            case IMPORT_RUN_FAILED ->
                jsonMapper.readValue(event.payload(), ImportRunFailedPayload.class);
            case IMPORT_RUN_RETRY_SCHEDULED ->
                jsonMapper.readValue(event.payload(), ImportRunRetryScheduledPayload.class);
          };
      new ConnectorIntegrationEvent(
          event.eventId(),
          eventType,
          event.tenantId(),
          event.aggregateType(),
          event.aggregateId(),
          event.occurredAt(),
          payload);
      return payload;
    } catch (JacksonException | IllegalArgumentException exception) {
      throw new ConnectorEventPublicationException(CONTRACT_REJECTED, false, exception);
    }
  }

  private static ConnectorIntegrationEventType requireSupportedContract(
      ConnectorIntegrationEventEnvelope event) {
    for (ConnectorIntegrationEventType type : ConnectorIntegrationEventType.values()) {
      if (type.eventType().equals(event.eventType())
          && type.schemaVersion() == event.schemaVersion()
          && type.aggregateType().equals(event.aggregateType())) {
        return type;
      }
    }
    throw new ConnectorEventPublicationException(CONTRACT_REJECTED, false, null);
  }

  private static ConnectorEventPublicationException classify(Throwable failure) {
    Throwable cause = Objects.requireNonNull(failure, "Kafka publication failure cannot be null");

    if (contains(
        cause,
        AuthenticationException.class,
        AuthorizationException.class,
        InvalidTopicException.class,
        RecordTooLargeException.class,
        SerializationException.class)) {
      return new ConnectorEventPublicationException(PUBLISH_REJECTED, false, cause);
    }
    if (contains(cause, RetriableException.class)) {
      return new ConnectorEventPublicationException(BROKER_RETRYABLE, true, cause);
    }
    if (contains(cause, KafkaException.class)) {
      return new ConnectorEventPublicationException(PUBLISH_FAILED, false, cause);
    }
    return new ConnectorEventPublicationException(PUBLISH_FAILED, false, cause);
  }

  @SafeVarargs
  private static boolean contains(Throwable failure, Class<? extends Throwable>... types) {
    Throwable current = failure;
    while (current != null) {
      for (Class<? extends Throwable> type : types) {
        if (type.isInstance(current)) {
          return true;
        }
      }
      current = current.getCause();
    }
    return false;
  }
}
