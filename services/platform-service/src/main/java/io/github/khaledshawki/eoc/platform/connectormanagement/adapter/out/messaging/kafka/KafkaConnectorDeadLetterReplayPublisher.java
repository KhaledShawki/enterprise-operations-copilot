package io.github.khaledshawki.eoc.platform.connectormanagement.adapter.out.messaging.kafka;

import io.github.khaledshawki.eoc.connectormanagement.application.exception.ConnectorEventPublicationException;
import io.github.khaledshawki.eoc.connectormanagement.application.model.recovery.ClaimedConnectorDeadLetterReplay;
import io.github.khaledshawki.eoc.connectormanagement.application.model.recovery.ConnectorDeadLetterHeader;
import io.github.khaledshawki.eoc.connectormanagement.application.port.out.ConnectorDeadLetterReplayPublisher;
import io.github.khaledshawki.eoc.platform.connectormanagement.adapter.messaging.kafka.ConnectorKafkaHeaders;
import io.github.khaledshawki.eoc.platform.connectormanagement.configuration.ConnectorKafkaProperties;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnExpression(
    "'${eoc.connector-events.transport:local}' == 'kafka' and "
        + "${eoc.connector-events.kafka.dead-letter-recovery.enabled:false}")
final class KafkaConnectorDeadLetterReplayPublisher implements ConnectorDeadLetterReplayPublisher {

  static final String ROUTE_REJECTED = "connector-dead-letter-replay-route-rejected";
  static final String PUBLISH_TIMEOUT = "kafka-publish-timeout";
  static final String PUBLISH_INTERRUPTED = "kafka-publish-interrupted";
  static final String PUBLISH_CANCELLED = "kafka-publish-cancelled";

  private final KafkaTemplate<String, String> kafkaTemplate;
  private final String sourceTopic;
  private final Duration sendTimeout;

  KafkaConnectorDeadLetterReplayPublisher(
      KafkaTemplate<String, String> kafkaTemplate, ConnectorKafkaProperties properties) {
    this.kafkaTemplate = Objects.requireNonNull(kafkaTemplate, "Kafka template cannot be null");
    ConnectorKafkaProperties requiredProperties =
        Objects.requireNonNull(properties, "Kafka properties cannot be null");
    this.sourceTopic = requiredProperties.topic();
    this.sendTimeout = requiredProperties.sendTimeout();
  }

  @Override
  public void publish(ClaimedConnectorDeadLetterReplay replay) {
    Objects.requireNonNull(replay, "Claimed connector replay cannot be null");
    if (!sourceTopic.equals(replay.sourceTopic())
        || replay.sourcePartition() != replay.deadLetter().partition()) {
      throw new ConnectorEventPublicationException(ROUTE_REJECTED, false, null);
    }

    RecordHeaders headers = new RecordHeaders();
    for (ConnectorDeadLetterHeader header : replay.headers()) {
      headers.add(header.name(), header.valueBytes());
    }
    headers
        .add(ConnectorKafkaHeaders.REPLAY_REQUEST_ID, bytes(replay.requestId().toString()))
        .add(
            ConnectorKafkaHeaders.REPLAY_GENERATION,
            bytes(Integer.toString(replay.replayGeneration())))
        .add(
            ConnectorKafkaHeaders.REPLAY_DLT_PARTITION,
            bytes(Integer.toString(replay.deadLetter().partition())))
        .add(
            ConnectorKafkaHeaders.REPLAY_DLT_OFFSET,
            bytes(Long.toString(replay.deadLetter().offset())));

    ProducerRecord<String, String> record =
        new ProducerRecord<>(
            replay.sourceTopic(),
            replay.sourcePartition(),
            replay.sourceTimestamp().toEpochMilli(),
            replay.key().orElse(null),
            replay.value().orElse(null),
            headers);
    CompletableFuture<SendResult<String, String>> send;
    try {
      send = kafkaTemplate.send(record);
    } catch (RuntimeException exception) {
      throw ConnectorKafkaPublicationFailureClassifier.classify(exception);
    }
    if (send == null) {
      throw new ConnectorEventPublicationException(
          ConnectorKafkaPublicationFailureClassifier.PUBLISH_FAILED,
          false,
          new IllegalStateException("KafkaTemplate returned a null future"));
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
      throw ConnectorKafkaPublicationFailureClassifier.classify(
          exception.getCause() == null ? exception : exception.getCause());
    }
  }

  private static byte[] bytes(String value) {
    return value.getBytes(StandardCharsets.UTF_8);
  }
}
