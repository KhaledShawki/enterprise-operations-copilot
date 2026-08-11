package io.github.khaledshawki.eoc.platform.connectormanagement.adapter.out.messaging.kafka;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.khaledshawki.eoc.connectormanagement.application.exception.ConnectorDeadLetterMalformedException;
import io.github.khaledshawki.eoc.connectormanagement.application.model.recovery.ConnectorDeadLetterPage;
import io.github.khaledshawki.eoc.connectormanagement.application.model.recovery.ConnectorDeadLetterRecord;
import io.github.khaledshawki.eoc.platform.connectormanagement.adapter.messaging.kafka.ConnectorKafkaHeaders;
import io.github.khaledshawki.eoc.platform.connectormanagement.configuration.ConnectorDeadLetterRecoveryProperties;
import io.github.khaledshawki.eoc.platform.connectormanagement.configuration.ConnectorKafkaConsumerProperties;
import io.github.khaledshawki.eoc.platform.connectormanagement.configuration.ConnectorKafkaProperties;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.support.KafkaHeaders;

class KafkaConnectorDeadLetterReaderTest {

  private static final String SOURCE_TOPIC = "eoc.connector.integration-events";
  private static final String DLT_TOPIC = "eoc.connector.integration-events.dlt";
  private static final Duration TIMEOUT = Duration.ofSeconds(1);
  private static final TopicPartition PARTITION = new TopicPartition(DLT_TOPIC, 2);

  @Test
  void readsABoundedPageWithoutCommittingAndPreservesOnlyReplayableHeaders() {
    Consumer<Object, Object> consumer = consumer();
    ConsumerRecord<Object, Object> first = record(19, SOURCE_TOPIC, 2);
    first.headers().add("traceparent", bytes("trace-1"));
    first.headers().add(KafkaHeaders.DLT_EXCEPTION_STACKTRACE, bytes("not replayed"));
    first.headers().add(ConnectorKafkaHeaders.REPLAY_GENERATION, bytes("1"));
    ConsumerRecord<Object, Object> second = record(20, SOURCE_TOPIC, 2);
    when(consumer.poll(any(Duration.class)))
        .thenReturn(new ConsumerRecords<>(Map.of(PARTITION, List.of(first, second)), Map.of()));
    KafkaConnectorDeadLetterReader reader = reader(consumer);

    ConnectorDeadLetterPage page = reader.readPage(2, 19, 1);

    assertEquals(20, page.nextOffset());
    assertEquals(21, page.endOffset());
    assertEquals(1, page.records().size());
    ConnectorDeadLetterRecord mapped = page.records().getFirst();
    assertEquals(1, mapped.replayGeneration());
    assertEquals(
        List.of("traceparent"),
        mapped.replayHeaders().stream().map(header -> header.name()).toList());
    verify(consumer).seek(PARTITION, 19);
    verify(consumer, never()).commitSync();
  }

  @Test
  void failsClosedWhenDltRoutingEvidenceDoesNotMatchTheConfiguredSource() {
    Consumer<Object, Object> consumer = consumer();
    when(consumer.poll(any(Duration.class)))
        .thenReturn(
            new ConsumerRecords<>(
                Map.of(PARTITION, List.of(record(19, "other.topic", 2))), Map.of()));
    KafkaConnectorDeadLetterReader reader = reader(consumer);

    assertThrows(ConnectorDeadLetterMalformedException.class, () -> reader.readPage(2, 19, 1));
  }

  @SuppressWarnings("unchecked")
  private static Consumer<Object, Object> consumer() {
    Consumer<Object, Object> consumer = mock(Consumer.class);
    when(consumer.beginningOffsets(List.of(PARTITION), TIMEOUT)).thenReturn(Map.of(PARTITION, 10L));
    when(consumer.endOffsets(List.of(PARTITION), TIMEOUT)).thenReturn(Map.of(PARTITION, 21L));
    return consumer;
  }

  @SuppressWarnings("unchecked")
  private static KafkaConnectorDeadLetterReader reader(Consumer<Object, Object> consumer) {
    ConsumerFactory<Object, Object> factory = mock(ConsumerFactory.class);
    when(factory.createConsumer()).thenReturn(consumer);
    return new KafkaConnectorDeadLetterReader(
        factory,
        new ConnectorKafkaProperties(SOURCE_TOPIC, Duration.ofSeconds(10)),
        new ConnectorKafkaConsumerProperties(
            true,
            "connector-group",
            DLT_TOPIC,
            4,
            Duration.ofSeconds(1),
            Duration.ofSeconds(10),
            1_048_576,
            3),
        new ConnectorDeadLetterRecoveryProperties(
            true, TIMEOUT, 100, 3, 5, Duration.ofSeconds(30), 1, Duration.ofSeconds(30)));
  }

  private static ConsumerRecord<Object, Object> record(
      long offset, String originalTopic, int originalPartition) {
    ConsumerRecord<Object, Object> record =
        new ConsumerRecord<>(DLT_TOPIC, 2, offset, "tenant:IMPORT_RUN:id", "{}");
    record
        .headers()
        .add(KafkaHeaders.DLT_ORIGINAL_TOPIC, bytes(originalTopic))
        .add(KafkaHeaders.DLT_ORIGINAL_PARTITION, integer(originalPartition))
        .add(KafkaHeaders.DLT_ORIGINAL_OFFSET, longValue(11))
        .add(
            KafkaHeaders.DLT_ORIGINAL_TIMESTAMP,
            longValue(Instant.parse("2026-08-10T11:59:00Z").toEpochMilli()))
        .add(KafkaHeaders.DLT_EXCEPTION_FQCN, bytes("java.lang.IllegalArgumentException"))
        .add(KafkaHeaders.DLT_EXCEPTION_MESSAGE, bytes("invalid payload"))
        .add(ConnectorKafkaHeaders.FAILURE_CODE, bytes("invalid-connector-event-payload"))
        .add(ConnectorKafkaHeaders.RETRYABLE, bytes("false"));
    return record;
  }

  private static byte[] bytes(String value) {
    return value.getBytes(StandardCharsets.UTF_8);
  }

  private static byte[] integer(int value) {
    return ByteBuffer.allocate(Integer.BYTES).putInt(value).array();
  }

  private static byte[] longValue(long value) {
    return ByteBuffer.allocate(Long.BYTES).putLong(value).array();
  }
}
