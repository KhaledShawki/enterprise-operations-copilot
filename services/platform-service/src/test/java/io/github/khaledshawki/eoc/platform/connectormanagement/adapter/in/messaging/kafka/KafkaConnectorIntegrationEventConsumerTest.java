package io.github.khaledshawki.eoc.platform.connectormanagement.adapter.in.messaging.kafka;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import io.github.khaledshawki.eoc.connectormanagement.application.exception.ConnectorEventConsumptionException;
import io.github.khaledshawki.eoc.connectormanagement.application.model.event.ConnectorIntegrationEventEnvelope;
import io.github.khaledshawki.eoc.connectormanagement.application.port.in.ConsumeConnectorIntegrationEventUseCase;
import java.time.Instant;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;

class KafkaConnectorIntegrationEventConsumerTest {

  private static final ConnectorIntegrationEventEnvelope EVENT =
      new ConnectorIntegrationEventEnvelope(
          UUID.fromString("00000000-0000-0000-0000-000000000081"),
          "connector.import-run.completed.v1",
          1,
          UUID.fromString("00000000-0000-0000-0000-000000000010"),
          "IMPORT_RUN",
          UUID.fromString("00000000-0000-0000-0000-000000000082"),
          "{}",
          Instant.parse("2026-08-09T19:00:00Z"));

  @Test
  void invokesTheApplicationInputPortForAValidPartitionKey() {
    ConsumeConnectorIntegrationEventUseCase useCase =
        mock(ConsumeConnectorIntegrationEventUseCase.class);
    KafkaConnectorIntegrationEventConsumer consumer = consumer(useCase);

    consumer.consume(record(expectedKey()));

    verify(useCase).consume(EVENT);
  }

  @Test
  void rejectsMismatchedRoutingEvidenceBeforeApplicationProcessing() {
    ConsumeConnectorIntegrationEventUseCase useCase =
        mock(ConsumeConnectorIntegrationEventUseCase.class);

    TerminalConnectorKafkaConsumptionException exception =
        assertThrows(
            TerminalConnectorKafkaConsumptionException.class,
            () -> consumer(useCase).consume(record("wrong-key")));

    assertEquals(KafkaConnectorIntegrationEventConsumer.KEY_MISMATCH, exception.failureCode());
    assertFalse(exception.retryable());
    verify(useCase, never()).consume(EVENT);
  }

  @Test
  void mapsRetryableInboxFailuresForBoundedKafkaRetry() {
    ConsumeConnectorIntegrationEventUseCase useCase =
        mock(ConsumeConnectorIntegrationEventUseCase.class);
    ConnectorEventConsumptionException cause =
        new ConnectorEventConsumptionException("connector-inbox-unavailable", true, null);
    doThrow(cause).when(useCase).consume(EVENT);

    RetryableConnectorKafkaConsumptionException exception =
        assertThrows(
            RetryableConnectorKafkaConsumptionException.class,
            () -> consumer(useCase).consume(record(expectedKey())));

    assertEquals("connector-inbox-unavailable", exception.failureCode());
    assertTrue(exception.retryable());
    assertSame(cause, exception.getCause());
  }

  @Test
  void mapsContractAndCollisionFailuresDirectlyToTerminalRecovery() {
    ConsumeConnectorIntegrationEventUseCase useCase =
        mock(ConsumeConnectorIntegrationEventUseCase.class);
    ConnectorEventConsumptionException cause =
        new ConnectorEventConsumptionException("connector-event-id-collision", false, null);
    doThrow(cause).when(useCase).consume(EVENT);

    TerminalConnectorKafkaConsumptionException exception =
        assertThrows(
            TerminalConnectorKafkaConsumptionException.class,
            () -> consumer(useCase).consume(record(expectedKey())));

    assertEquals("connector-event-id-collision", exception.failureCode());
    assertFalse(exception.retryable());
    assertSame(cause, exception.getCause());
  }

  @Test
  void treatsUnknownApplicationFailuresAsRetryableButBounded() {
    ConsumeConnectorIntegrationEventUseCase useCase =
        mock(ConsumeConnectorIntegrationEventUseCase.class);
    IllegalStateException cause = new IllegalStateException("temporary dependency failure");
    doThrow(cause).when(useCase).consume(EVENT);

    RetryableConnectorKafkaConsumptionException exception =
        assertThrows(
            RetryableConnectorKafkaConsumptionException.class,
            () -> consumer(useCase).consume(record(expectedKey())));

    assertEquals(
        KafkaConnectorIntegrationEventConsumer.CONSUMPTION_FAILED, exception.failureCode());
    assertSame(cause, exception.getCause());
  }

  private static KafkaConnectorIntegrationEventConsumer consumer(
      ConsumeConnectorIntegrationEventUseCase useCase) {
    KafkaConnectorIntegrationEventDecoder decoder =
        mock(KafkaConnectorIntegrationEventDecoder.class);
    org.mockito.Mockito.when(decoder.decode("wire-value")).thenReturn(EVENT);
    return new KafkaConnectorIntegrationEventConsumer(decoder, useCase);
  }

  private static ConsumerRecord<String, String> record(String key) {
    return new ConsumerRecord<>("events", 0, 42L, key, "wire-value");
  }

  private static String expectedKey() {
    return EVENT.tenantId() + ":" + EVENT.aggregateType() + ":" + EVENT.aggregateId();
  }
}
