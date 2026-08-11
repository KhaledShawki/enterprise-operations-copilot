package io.github.khaledshawki.eoc.platform.connectormanagement.adapter.out.messaging.kafka;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.khaledshawki.eoc.connectormanagement.application.exception.ConnectorEventPublicationException;
import io.github.khaledshawki.eoc.connectormanagement.application.model.recovery.ClaimedConnectorDeadLetterReplay;
import io.github.khaledshawki.eoc.connectormanagement.application.model.recovery.ConnectorDeadLetterHeader;
import io.github.khaledshawki.eoc.connectormanagement.application.model.recovery.ConnectorDeadLetterReference;
import io.github.khaledshawki.eoc.platform.connectormanagement.adapter.messaging.kafka.ConnectorKafkaHeaders;
import io.github.khaledshawki.eoc.platform.connectormanagement.configuration.ConnectorKafkaProperties;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;

class KafkaConnectorDeadLetterReplayPublisherTest {

  private static final String SOURCE_TOPIC = "eoc.connector.integration-events";

  @Test
  void republishesTheImmutableRecordToItsOriginalPartitionWithRecoveryEvidence() {
    KafkaTemplate<String, String> template = template();
    when(template.send(any(ProducerRecord.class)))
        .thenReturn(CompletableFuture.completedFuture(null));
    KafkaConnectorDeadLetterReplayPublisher publisher = publisher(template);

    publisher.publish(replay(SOURCE_TOPIC, 2));

    @SuppressWarnings("unchecked")
    ArgumentCaptor<ProducerRecord<String, String>> captor =
        ArgumentCaptor.forClass((Class) ProducerRecord.class);
    verify(template).send(captor.capture());
    ProducerRecord<String, String> record = captor.getValue();
    assertEquals(SOURCE_TOPIC, record.topic());
    assertEquals(2, record.partition());
    assertEquals("tenant:IMPORT_RUN:id", record.key());
    assertEquals("{\"eventId\":\"stable\"}", record.value());
    assertEquals(Instant.parse("2026-08-10T11:59:00Z").toEpochMilli(), record.timestamp());
    assertEquals("trace-1", text(record, "traceparent"));
    assertEquals(
        "00000000-0000-0000-0000-000000000054",
        text(record, ConnectorKafkaHeaders.REPLAY_REQUEST_ID));
    assertEquals("2", text(record, ConnectorKafkaHeaders.REPLAY_GENERATION));
    assertEquals("19", text(record, ConnectorKafkaHeaders.REPLAY_DLT_OFFSET));
  }

  @Test
  void rejectsTamperedSourceRoutingBeforePublishing() {
    KafkaTemplate<String, String> template = template();
    KafkaConnectorDeadLetterReplayPublisher publisher = publisher(template);

    ConnectorEventPublicationException exception =
        assertThrows(
            ConnectorEventPublicationException.class,
            () -> publisher.publish(replay("another.topic", 2)));

    assertEquals(KafkaConnectorDeadLetterReplayPublisher.ROUTE_REJECTED, exception.failureCode());
    assertFalse(exception.retryable());
    verify(template, never()).send(any(ProducerRecord.class));
  }

  private static KafkaConnectorDeadLetterReplayPublisher publisher(
      KafkaTemplate<String, String> template) {
    return new KafkaConnectorDeadLetterReplayPublisher(
        template, new ConnectorKafkaProperties(SOURCE_TOPIC, Duration.ofSeconds(1)));
  }

  private static ClaimedConnectorDeadLetterReplay replay(String topic, int partition) {
    return new ClaimedConnectorDeadLetterReplay(
        UUID.fromString("00000000-0000-0000-0000-000000000054"),
        new ConnectorDeadLetterReference(2, 19),
        topic,
        partition,
        11,
        Instant.parse("2026-08-10T11:59:00Z"),
        Optional.of("tenant:IMPORT_RUN:id"),
        Optional.of("{\"eventId\":\"stable\"}"),
        List.of(
            ConnectorDeadLetterHeader.fromBytes(
                "traceparent", "trace-1".getBytes(StandardCharsets.UTF_8))),
        2,
        1,
        "worker-1",
        Instant.parse("2026-08-10T12:00:00Z"));
  }

  private static String text(ProducerRecord<String, String> record, String header) {
    return new String(record.headers().lastHeader(header).value(), StandardCharsets.UTF_8);
  }

  @SuppressWarnings("unchecked")
  private static KafkaTemplate<String, String> template() {
    return mock(KafkaTemplate.class);
  }
}
