package io.github.khaledshawki.eoc.platform.operations.adapter.out.messaging.kafka;

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

import io.github.khaledshawki.eoc.operations.application.exception.OperationsEventPublicationException;
import io.github.khaledshawki.eoc.operations.application.model.event.OperationsIntegrationEventEnvelope;
import io.github.khaledshawki.eoc.platform.operations.configuration.OperationsKafkaProperties;
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

class KafkaOperationsIntegrationEventPublisherTest {

  private static final String TOPIC = "eoc.operations.integration-events";
  private static final UUID EVENT_ID = UUID.fromString("00000000-0000-0000-0000-000000000101");
  private static final UUID TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000102");
  private static final UUID INVOICE_ID = UUID.fromString("00000000-0000-0000-0000-000000000103");
  private static final UUID CUSTOMER_ID = UUID.fromString("00000000-0000-0000-0000-000000000104");
  private static final Instant OCCURRED_AT = Instant.parse("2026-08-11T12:00:00Z");

  private final JsonMapper jsonMapper = JsonMapper.builder().findAndAddModules().build();

  @Test
  void publishesThePortableVersionedEnvelopeWithDeterministicRoutingEvidence() throws Exception {
    KafkaTemplate<String, String> kafkaTemplate = kafkaTemplate();
    when(kafkaTemplate.send(any(ProducerRecord.class)))
        .thenReturn(CompletableFuture.completedFuture(null));
    KafkaOperationsIntegrationEventPublisher publisher =
        publisher(kafkaTemplate, Duration.ofSeconds(1));

    publisher.publish(invoiceEvent(invoicePayload()));

    @SuppressWarnings("unchecked")
    ArgumentCaptor<ProducerRecord<String, String>> captor =
        ArgumentCaptor.forClass((Class) ProducerRecord.class);
    verify(kafkaTemplate).send(captor.capture());
    ProducerRecord<String, String> record = captor.getValue();
    assertEquals(TOPIC, record.topic());
    assertEquals(TENANT_ID + ":INVOICE:" + INVOICE_ID, record.key());
    assertEquals(OCCURRED_AT.toEpochMilli(), record.timestamp());

    @SuppressWarnings("unchecked")
    Map<String, Object> wire = jsonMapper.readValue(record.value(), Map.class);
    assertEquals(EVENT_ID.toString(), wire.get("eventId"));
    assertEquals("operations.invoice.synchronized.v1", wire.get("eventType"));
    assertEquals(1, ((Number) wire.get("schemaVersion")).intValue());
    assertEquals(TENANT_ID.toString(), wire.get("tenantId"));
    assertEquals("INVOICE", wire.get("aggregateType"));
    assertEquals(INVOICE_ID.toString(), wire.get("aggregateId"));
    assertEquals(7L, ((Number) wire.get("aggregateVersion")).longValue());
    assertEquals(OCCURRED_AT.toString(), wire.get("occurredAt"));
    assertTrue(wire.get("payload") instanceof Map<?, ?>);
    assertFalse(wire.containsKey("publicationAttempt"));
    assertFalse(wire.containsKey("claimOwner"));
    assertFalse(wire.containsKey("claimedAt"));
  }

  @Test
  void rejectsMalformedOrNonObjectPayloadBeforeCallingKafka() {
    KafkaTemplate<String, String> kafkaTemplate = kafkaTemplate();
    KafkaOperationsIntegrationEventPublisher publisher =
        publisher(kafkaTemplate, Duration.ofSeconds(1));

    OperationsEventPublicationException malformed =
        assertThrows(
            OperationsEventPublicationException.class,
            () -> publisher.publish(invoiceEvent("not-json")));
    OperationsEventPublicationException nonObject =
        assertThrows(
            OperationsEventPublicationException.class,
            () -> publisher.publish(invoiceEvent("[1, 2, 3]")));

    assertEquals("kafka-event-contract-rejected", malformed.failureCode());
    assertFalse(malformed.retryable());
    assertEquals("kafka-event-contract-rejected", nonObject.failureCode());
    assertFalse(nonObject.retryable());
    verify(kafkaTemplate, never()).send(any(ProducerRecord.class));
  }

  @Test
  void rejectsUnsupportedContractsAndPayloadIdentityMismatchBeforeCallingKafka() {
    KafkaTemplate<String, String> kafkaTemplate = kafkaTemplate();
    KafkaOperationsIntegrationEventPublisher publisher =
        publisher(kafkaTemplate, Duration.ofSeconds(1));
    OperationsIntegrationEventEnvelope unsupported =
        new OperationsIntegrationEventEnvelope(
            EVENT_ID,
            "operations.invoice.synchronized.v2",
            2,
            TENANT_ID,
            "INVOICE",
            INVOICE_ID,
            7,
            invoicePayload(),
            OCCURRED_AT);
    String mismatchedPayload =
        invoicePayload().replace(INVOICE_ID.toString(), UUID.randomUUID().toString());

    OperationsEventPublicationException unsupportedFailure =
        assertThrows(
            OperationsEventPublicationException.class, () -> publisher.publish(unsupported));
    OperationsEventPublicationException mismatchFailure =
        assertThrows(
            OperationsEventPublicationException.class,
            () -> publisher.publish(invoiceEvent(mismatchedPayload)));

    assertEquals("kafka-event-contract-rejected", unsupportedFailure.failureCode());
    assertFalse(unsupportedFailure.retryable());
    assertEquals("kafka-event-contract-rejected", mismatchFailure.failureCode());
    assertFalse(mismatchFailure.retryable());
    verify(kafkaTemplate, never()).send(any(ProducerRecord.class));
  }

  @Test
  void classifiesSynchronousAndAsynchronousRetriableKafkaFailures() {
    KafkaTemplate<String, String> synchronousTemplate = kafkaTemplate();
    TimeoutException synchronousFailure = new TimeoutException("metadata unavailable");
    when(synchronousTemplate.send(any(ProducerRecord.class))).thenThrow(synchronousFailure);
    KafkaTemplate<String, String> asynchronousTemplate = kafkaTemplate();
    TimeoutException asynchronousFailure = new TimeoutException("broker did not acknowledge");
    when(asynchronousTemplate.send(any(ProducerRecord.class)))
        .thenReturn(failedFuture(asynchronousFailure));

    OperationsEventPublicationException synchronous =
        assertThrows(
            OperationsEventPublicationException.class,
            () ->
                publisher(synchronousTemplate, Duration.ofSeconds(1))
                    .publish(invoiceEvent(invoicePayload())));
    OperationsEventPublicationException asynchronous =
        assertThrows(
            OperationsEventPublicationException.class,
            () ->
                publisher(asynchronousTemplate, Duration.ofSeconds(1))
                    .publish(invoiceEvent(invoicePayload())));

    assertEquals("kafka-broker-retryable-error", synchronous.failureCode());
    assertTrue(synchronous.retryable());
    assertSame(synchronousFailure, synchronous.getCause());
    assertEquals("kafka-broker-retryable-error", asynchronous.failureCode());
    assertTrue(asynchronous.retryable());
    assertSame(asynchronousFailure, asynchronous.getCause());
  }

  @Test
  void classifiesPermanentKafkaRejectionsAsTerminal() {
    KafkaTemplate<String, String> kafkaTemplate = kafkaTemplate();
    InvalidTopicException brokerFailure = new InvalidTopicException("invalid topic");
    when(kafkaTemplate.send(any(ProducerRecord.class))).thenReturn(failedFuture(brokerFailure));

    OperationsEventPublicationException exception =
        assertThrows(
            OperationsEventPublicationException.class,
            () ->
                publisher(kafkaTemplate, Duration.ofSeconds(1))
                    .publish(invoiceEvent(invoicePayload())));

    assertEquals("kafka-publish-rejected", exception.failureCode());
    assertFalse(exception.retryable());
    assertSame(brokerFailure, exception.getCause());
  }

  @Test
  void classifiesApplicationAcknowledgementTimeoutAndCancellationAsRetryable() {
    KafkaTemplate<String, String> timeoutTemplate = kafkaTemplate();
    when(timeoutTemplate.send(any(ProducerRecord.class))).thenReturn(new CompletableFuture<>());
    KafkaTemplate<String, String> cancelledTemplate = kafkaTemplate();
    CompletableFuture<SendResult<String, String>> cancelled = new CompletableFuture<>();
    cancelled.cancel(false);
    when(cancelledTemplate.send(any(ProducerRecord.class))).thenReturn(cancelled);

    OperationsEventPublicationException timeout =
        assertThrows(
            OperationsEventPublicationException.class,
            () ->
                publisher(timeoutTemplate, Duration.ofMillis(5))
                    .publish(invoiceEvent(invoicePayload())));
    OperationsEventPublicationException cancellation =
        assertThrows(
            OperationsEventPublicationException.class,
            () ->
                publisher(cancelledTemplate, Duration.ofSeconds(1))
                    .publish(invoiceEvent(invoicePayload())));

    assertEquals("kafka-publish-timeout", timeout.failureCode());
    assertTrue(timeout.retryable());
    assertEquals("kafka-publish-cancelled", cancellation.failureCode());
    assertTrue(cancellation.retryable());
  }

  @Test
  void preservesInterruptStatusAndRejectsANullKafkaFuture() {
    KafkaTemplate<String, String> interruptedTemplate = kafkaTemplate();
    when(interruptedTemplate.send(any(ProducerRecord.class))).thenReturn(new CompletableFuture<>());
    KafkaTemplate<String, String> nullTemplate = kafkaTemplate();
    when(nullTemplate.send(any(ProducerRecord.class))).thenReturn(null);

    Thread.currentThread().interrupt();
    try {
      OperationsEventPublicationException interrupted =
          assertThrows(
              OperationsEventPublicationException.class,
              () ->
                  publisher(interruptedTemplate, Duration.ofSeconds(1))
                      .publish(invoiceEvent(invoicePayload())));
      assertEquals("kafka-publish-interrupted", interrupted.failureCode());
      assertTrue(interrupted.retryable());
      assertTrue(Thread.currentThread().isInterrupted());
    } finally {
      Thread.interrupted();
    }

    OperationsEventPublicationException nullFuture =
        assertThrows(
            OperationsEventPublicationException.class,
            () ->
                publisher(nullTemplate, Duration.ofSeconds(1))
                    .publish(invoiceEvent(invoicePayload())));
    assertEquals("kafka-publish-failed", nullFuture.failureCode());
    assertFalse(nullFuture.retryable());
  }

  private KafkaOperationsIntegrationEventPublisher publisher(
      KafkaTemplate<String, String> kafkaTemplate, Duration timeout) {
    return new KafkaOperationsIntegrationEventPublisher(
        kafkaTemplate, jsonMapper, new OperationsKafkaProperties(TOPIC, timeout));
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

  private static OperationsIntegrationEventEnvelope invoiceEvent(String payload) {
    return new OperationsIntegrationEventEnvelope(
        EVENT_ID,
        "operations.invoice.synchronized.v1",
        1,
        TENANT_ID,
        "INVOICE",
        INVOICE_ID,
        7,
        payload,
        OCCURRED_AT);
  }

  private static String invoicePayload() {
    return """
        {
          "invoiceId": "%s",
          "customerId": "%s",
          "invoiceNumber": "INV-1001",
          "originalAmount": {"amount": 100.00, "currency": "EUR"},
          "paidAmount": {"amount": 0.00, "currency": "EUR"},
          "issueDate": "2026-08-01",
          "dueDate": "2026-08-31",
          "cancelled": false,
          "status": "OPEN",
          "source": {
            "sourceSystemId": "00000000-0000-0000-0000-000000000105",
            "sourceIdentityKind": "SOURCE_RECORD_ID",
            "sourceIdentity": "source-invoice-1001",
            "sourceVersion": "version-1",
            "sourceModifiedAt": null
          }
        }
        """
        .formatted(INVOICE_ID, CUSTOMER_ID);
  }
}
