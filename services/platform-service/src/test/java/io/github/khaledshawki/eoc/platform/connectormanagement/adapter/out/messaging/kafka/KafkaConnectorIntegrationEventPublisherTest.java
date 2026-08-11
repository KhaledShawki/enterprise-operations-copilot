package io.github.khaledshawki.eoc.platform.connectormanagement.adapter.out.messaging.kafka;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.khaledshawki.eoc.connectormanagement.application.exception.ConnectorEventPublicationException;
import io.github.khaledshawki.eoc.connectormanagement.application.model.event.ConnectorIntegrationEventEnvelope;
import io.github.khaledshawki.eoc.platform.connectormanagement.configuration.ConnectorKafkaProperties;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.errors.InvalidTopicException;
import org.apache.kafka.common.errors.TimeoutException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import tools.jackson.databind.json.JsonMapper;

class KafkaConnectorIntegrationEventPublisherTest {

  private static final String TOPIC = "eoc.connector.integration-events";
  private static final UUID EVENT_ID = UUID.fromString("00000000-0000-0000-0000-000000000081");
  private static final UUID TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000010");
  private static final UUID AGGREGATE_ID = UUID.fromString("00000000-0000-0000-0000-000000000082");
  private static final Instant OCCURRED_AT = Instant.parse("2026-08-09T19:00:00Z");

  private final JsonMapper jsonMapper = JsonMapper.builder().build();

  @Test
  void shouldPublishPortableEnvelopeWithDeterministicAggregateKeyAndOccurrenceTimestamp()
      throws Exception {
    KafkaTemplate<String, String> kafkaTemplate = kafkaTemplate();
    when(kafkaTemplate.send(any(ProducerRecord.class)))
        .thenReturn(CompletableFuture.completedFuture(null));
    KafkaConnectorIntegrationEventPublisher publisher =
        publisher(kafkaTemplate, Duration.ofSeconds(1));

    publisher.publish(event(completedPayload()));

    @SuppressWarnings("unchecked")
    ArgumentCaptor<ProducerRecord<String, String>> captor =
        ArgumentCaptor.forClass((Class) ProducerRecord.class);
    verify(kafkaTemplate).send(captor.capture());
    ProducerRecord<String, String> record = captor.getValue();
    assertEquals(TOPIC, record.topic());
    assertEquals(TENANT_ID + ":IMPORT_RUN:" + AGGREGATE_ID, record.key());
    assertEquals(OCCURRED_AT.toEpochMilli(), record.timestamp());

    @SuppressWarnings("unchecked")
    Map<String, Object> wire = jsonMapper.readValue(record.value(), Map.class);
    assertEquals(EVENT_ID.toString(), wire.get("eventId"));
    assertEquals("connector.import-run.completed.v1", wire.get("eventType"));
    assertEquals(1, ((Number) wire.get("schemaVersion")).intValue());
    assertEquals(TENANT_ID.toString(), wire.get("tenantId"));
    assertEquals("IMPORT_RUN", wire.get("aggregateType"));
    assertEquals(AGGREGATE_ID.toString(), wire.get("aggregateId"));
    assertEquals(OCCURRED_AT.toString(), wire.get("occurredAt"));
    assertTrue(wire.get("payload") instanceof Map<?, ?>);
    assertFalse(wire.containsKey("publicationAttempt"));
    assertFalse(wire.containsKey("claimOwner"));
    assertFalse(wire.containsKey("claimedAt"));
  }

  @Test
  void shouldRejectNonObjectJsonPayloadBeforeCallingKafka() {
    KafkaTemplate<String, String> kafkaTemplate = kafkaTemplate();
    KafkaConnectorIntegrationEventPublisher publisher =
        publisher(kafkaTemplate, Duration.ofSeconds(1));

    ConnectorEventPublicationException exception =
        assertThrows(
            ConnectorEventPublicationException.class, () -> publisher.publish(event("[1, 2, 3]")));

    assertEquals("kafka-event-contract-rejected", exception.failureCode());
    assertFalse(exception.retryable());
    verify(kafkaTemplate, never()).send(any(ProducerRecord.class));
  }

  @Test
  void shouldRejectMalformedPayloadBeforeCallingKafka() {
    KafkaTemplate<String, String> kafkaTemplate = kafkaTemplate();
    KafkaConnectorIntegrationEventPublisher publisher =
        publisher(kafkaTemplate, Duration.ofSeconds(1));

    ConnectorEventPublicationException exception =
        assertThrows(
            ConnectorEventPublicationException.class, () -> publisher.publish(event("not-json")));

    assertEquals("kafka-event-contract-rejected", exception.failureCode());
    assertFalse(exception.retryable());
    verify(kafkaTemplate, never()).send(any(ProducerRecord.class));
  }

  @Test
  void shouldRejectUnsupportedEventContractBeforeCallingKafka() {
    KafkaTemplate<String, String> kafkaTemplate = kafkaTemplate();
    KafkaConnectorIntegrationEventPublisher publisher =
        publisher(kafkaTemplate, Duration.ofSeconds(1));
    ConnectorIntegrationEventEnvelope unsupported =
        new ConnectorIntegrationEventEnvelope(
            EVENT_ID,
            "connector.import-run.completed.v2",
            2,
            TENANT_ID,
            "IMPORT_RUN",
            AGGREGATE_ID,
            completedPayload(),
            OCCURRED_AT);

    ConnectorEventPublicationException exception =
        assertThrows(
            ConnectorEventPublicationException.class, () -> publisher.publish(unsupported));

    assertEquals("kafka-event-contract-rejected", exception.failureCode());
    assertFalse(exception.retryable());
    verify(kafkaTemplate, never()).send(any(ProducerRecord.class));
  }

  @Test
  void shouldClassifySynchronousKafkaSendFailureForOutboxRetry() {
    KafkaTemplate<String, String> kafkaTemplate = kafkaTemplate();
    TimeoutException brokerFailure = new TimeoutException("metadata unavailable");
    when(kafkaTemplate.send(any(ProducerRecord.class))).thenThrow(brokerFailure);
    KafkaConnectorIntegrationEventPublisher publisher =
        publisher(kafkaTemplate, Duration.ofSeconds(1));

    ConnectorEventPublicationException exception =
        assertThrows(
            ConnectorEventPublicationException.class,
            () -> publisher.publish(event(completedPayload())));

    assertEquals("kafka-broker-retryable-error", exception.failureCode());
    assertTrue(exception.retryable());
    assertSame(brokerFailure, exception.getCause());
  }

  @Test
  void shouldClassifyKafkaRetriableFailuresForOutboxRetry() {
    KafkaTemplate<String, String> kafkaTemplate = kafkaTemplate();
    TimeoutException brokerFailure = new TimeoutException("broker did not acknowledge");
    when(kafkaTemplate.send(any(ProducerRecord.class))).thenReturn(failedFuture(brokerFailure));
    KafkaConnectorIntegrationEventPublisher publisher =
        publisher(kafkaTemplate, Duration.ofSeconds(1));

    ConnectorEventPublicationException exception =
        assertThrows(
            ConnectorEventPublicationException.class,
            () -> publisher.publish(event(completedPayload())));

    assertEquals("kafka-broker-retryable-error", exception.failureCode());
    assertTrue(exception.retryable());
    assertSame(brokerFailure, exception.getCause());
  }

  @Test
  void shouldClassifyPermanentKafkaRejectionsAsTerminal() {
    KafkaTemplate<String, String> kafkaTemplate = kafkaTemplate();
    InvalidTopicException brokerFailure = new InvalidTopicException("invalid topic");
    when(kafkaTemplate.send(any(ProducerRecord.class))).thenReturn(failedFuture(brokerFailure));
    KafkaConnectorIntegrationEventPublisher publisher =
        publisher(kafkaTemplate, Duration.ofSeconds(1));

    ConnectorEventPublicationException exception =
        assertThrows(
            ConnectorEventPublicationException.class,
            () -> publisher.publish(event(completedPayload())));

    assertEquals("kafka-publish-rejected", exception.failureCode());
    assertFalse(exception.retryable());
    assertSame(brokerFailure, exception.getCause());
  }

  @Test
  void shouldClassifyApplicationAckWaitTimeoutAsRetryable() {
    KafkaTemplate<String, String> kafkaTemplate = kafkaTemplate();
    when(kafkaTemplate.send(any(ProducerRecord.class))).thenReturn(new CompletableFuture<>());
    KafkaConnectorIntegrationEventPublisher publisher =
        publisher(kafkaTemplate, Duration.ofMillis(5));

    ConnectorEventPublicationException exception =
        assertThrows(
            ConnectorEventPublicationException.class,
            () -> publisher.publish(event(completedPayload())));

    assertEquals("kafka-publish-timeout", exception.failureCode());
    assertTrue(exception.retryable());
  }

  @Test
  void shouldClassifyCancelledSendAsRetryable() {
    KafkaTemplate<String, String> kafkaTemplate = kafkaTemplate();
    CompletableFuture<SendResult<String, String>> cancelled = new CompletableFuture<>();
    cancelled.cancel(false);
    when(kafkaTemplate.send(any(ProducerRecord.class))).thenReturn(cancelled);
    KafkaConnectorIntegrationEventPublisher publisher =
        publisher(kafkaTemplate, Duration.ofSeconds(1));

    ConnectorEventPublicationException exception =
        assertThrows(
            ConnectorEventPublicationException.class,
            () -> publisher.publish(event(completedPayload())));

    assertEquals("kafka-publish-cancelled", exception.failureCode());
    assertTrue(exception.retryable());
  }

  @Test
  void shouldPreserveInterruptStatusAndScheduleDurableRetry() {
    KafkaTemplate<String, String> kafkaTemplate = kafkaTemplate();
    when(kafkaTemplate.send(any(ProducerRecord.class))).thenReturn(new CompletableFuture<>());
    KafkaConnectorIntegrationEventPublisher publisher =
        publisher(kafkaTemplate, Duration.ofSeconds(1));

    Thread.currentThread().interrupt();
    try {
      ConnectorEventPublicationException exception =
          assertThrows(
              ConnectorEventPublicationException.class,
              () -> publisher.publish(event(completedPayload())));

      assertEquals("kafka-publish-interrupted", exception.failureCode());
      assertTrue(exception.retryable());
      assertTrue(Thread.currentThread().isInterrupted());
    } finally {
      Thread.interrupted();
    }
  }

  @Test
  void shouldRejectNullKafkaFutureAsTerminalPublisherContractViolation() {
    KafkaTemplate<String, String> kafkaTemplate = kafkaTemplate();
    when(kafkaTemplate.send(any(ProducerRecord.class))).thenReturn(null);
    KafkaConnectorIntegrationEventPublisher publisher =
        publisher(kafkaTemplate, Duration.ofSeconds(1));

    ConnectorEventPublicationException exception =
        assertThrows(
            ConnectorEventPublicationException.class,
            () -> publisher.publish(event(completedPayload())));

    assertEquals("kafka-publish-failed", exception.failureCode());
    assertFalse(exception.retryable());
  }

  private KafkaConnectorIntegrationEventPublisher publisher(
      KafkaTemplate<String, String> kafkaTemplate, Duration timeout) {
    return new KafkaConnectorIntegrationEventPublisher(
        kafkaTemplate, jsonMapper, new ConnectorKafkaProperties(TOPIC, timeout));
  }

  @SuppressWarnings("unchecked")
  private static KafkaTemplate<String, String> kafkaTemplate() {
    return mock(KafkaTemplate.class);
  }

  private static CompletableFuture<SendResult<String, String>> failedFuture(Throwable failure) {
    CompletableFuture<SendResult<String, String>> future = new CompletableFuture<>();
    future.completeExceptionally(failure);
    return future;
  }

  private static ConnectorIntegrationEventEnvelope event(String payload) {
    return new ConnectorIntegrationEventEnvelope(
        EVENT_ID,
        "connector.import-run.completed.v1",
        1,
        TENANT_ID,
        "IMPORT_RUN",
        AGGREGATE_ID,
        payload,
        OCCURRED_AT);
  }

  private static String completedPayload() {
    return """
        {
          "connectorId": "00000000-0000-0000-0000-000000000083",
          "importType": "CUSTOMERS",
          "importMode": "INCREMENTAL",
          "status": "COMPLETED",
          "fetchedCount": 2,
          "acceptedCount": 2,
          "rejectedCount": 0,
          "duplicateCount": 0,
          "attemptCount": 1
        }
        """;
  }
}
