package io.github.khaledshawki.eoc.platform.connectormanagement.adapter.out.messaging.kafka;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.khaledshawki.eoc.connectormanagement.application.exception.ConnectorDeadLetterReplayLimitExceededException;
import io.github.khaledshawki.eoc.connectormanagement.application.model.authorization.ConnectorActor;
import io.github.khaledshawki.eoc.connectormanagement.application.model.recovery.ConnectorDeadLetterRecord;
import io.github.khaledshawki.eoc.connectormanagement.application.model.recovery.ConnectorDeadLetterReference;
import io.github.khaledshawki.eoc.connectormanagement.application.model.recovery.ConnectorDeadLetterReplayRequest;
import io.github.khaledshawki.eoc.connectormanagement.application.model.recovery.ConnectorDeadLetterReplayStatus;
import io.github.khaledshawki.eoc.connectormanagement.application.model.recovery.RequestConnectorDeadLetterReplayCommand;
import io.github.khaledshawki.eoc.connectormanagement.application.port.in.InspectConnectorDeadLettersUseCase;
import io.github.khaledshawki.eoc.connectormanagement.application.port.in.RequestConnectorDeadLetterReplayUseCase;
import io.github.khaledshawki.eoc.platform.TestcontainersConfiguration;
import io.github.khaledshawki.eoc.platform.connectormanagement.adapter.messaging.kafka.ConnectorKafkaHeaders;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.kafka.support.SendResult;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;

@Testcontainers
@SpringBootTest(
    properties = {
      "eoc.connector-events.transport=kafka",
      "eoc.connector-events.kafka.topic=eoc.connector.integration-events.recovery-it",
      "eoc.connector-events.kafka.consumer.enabled=true",
      "eoc.connector-events.kafka.consumer.group-id=eoc-connector-recovery-it",
      "eoc.connector-events.kafka.consumer.dlt-topic=eoc.connector.integration-events.recovery-it.dlt",
      "eoc.connector-events.kafka.consumer.max-attempts=1",
      "eoc.connector-events.kafka.consumer.retry-backoff=0ms",
      "eoc.connector-events.kafka.consumer.dlt-send-timeout=5s",
      "eoc.connector-events.kafka.consumer.concurrency=1",
      "eoc.connector-events.kafka.dead-letter-recovery.enabled=true",
      "eoc.connector-events.kafka.dead-letter-recovery.inspection-timeout=2s",
      "eoc.connector-events.kafka.dead-letter-recovery.max-page-size=100",
      "eoc.connector-events.kafka.dead-letter-recovery.max-replay-generation=1",
      "eoc.connector-events.kafka.dead-letter-recovery.max-attempts=3",
      "eoc.connector-events.kafka.dead-letter-recovery.retry-delay=200ms",
      "eoc.connector-events.kafka.dead-letter-recovery.batch-size=1",
      "eoc.connector-events.kafka.dead-letter-recovery.claim-lease=30s",
      "eoc.connector-events.kafka.dead-letter-recovery.initial-delay-ms=100",
      "eoc.connector-events.kafka.dead-letter-recovery.fixed-delay-ms=100",
      "eoc.connector-outbox.relay-enabled=false",
      "spring.kafka.consumer.auto-offset-reset=earliest",
      "spring.kafka.admin.fail-fast=true"
    })
@Import({
  TestcontainersConfiguration.class,
  KafkaConnectorDeadLetterRecoveryIT.KafkaTopicsConfiguration.class
})
class KafkaConnectorDeadLetterRecoveryIT {

  private static final String SOURCE_TOPIC = "eoc.connector.integration-events.recovery-it";
  private static final String DLT_TOPIC = SOURCE_TOPIC + ".dlt";
  private static final String GROUP_ID = "eoc-connector-recovery-it";
  private static final int PARTITION = 1;
  private static final Instant SOURCE_TIMESTAMP = Instant.parse("2026-08-10T11:59:00Z");

  @Container static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka-native:4.2.1");

  @Autowired private KafkaTemplate<String, String> kafkaTemplate;
  @Autowired private InspectConnectorDeadLettersUseCase inspectUseCase;
  @Autowired private RequestConnectorDeadLetterReplayUseCase replayUseCase;
  @Autowired private JdbcTemplate jdbcTemplate;

  @DynamicPropertySource
  static void kafkaProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
  }

  @BeforeEach
  void setUp() {
    jdbcTemplate.update("DELETE FROM connector_dead_letter_replay_requests");
    jdbcTemplate.update("DELETE FROM connector_import_run_event_projection");
    jdbcTemplate.update("DELETE FROM connector_inbox_events");
  }

  @Test
  void inspectsAuditsAndReplaysWithoutAdvancingDltOffsetsOrCreatingAnInfiniteLoop()
      throws Exception {
    String key = "malformed-key-" + UUID.randomUUID();
    String value = "{\"testMarker\":\"" + UUID.randomUUID() + "\"";
    kafkaTemplate
        .send(
            new ProducerRecord<>(
                SOURCE_TOPIC, PARTITION, SOURCE_TIMESTAMP.toEpochMilli(), key, value))
        .get(10, TimeUnit.SECONDS);

    ConnectorDeadLetterRecord first = awaitRecord(key, 0);
    assertEquals(PARTITION, first.reference().partition());
    assertEquals(PARTITION, first.sourcePartition());
    assertEquals(SOURCE_TIMESTAMP, first.sourceTimestamp());
    assertEquals(value, first.value().orElseThrow());

    RequestConnectorDeadLetterReplayCommand command =
        new RequestConnectorDeadLetterReplayCommand(
            new ConnectorActor("https://issuer.example", "platform-admin-42"),
            first.reference(),
            "malformed contract corrected by operator");
    ConnectorDeadLetterReplayRequest requested = replayUseCase.request(command);
    ConnectorDeadLetterReplayRequest duplicate = replayUseCase.request(command);
    assertEquals(requested.requestId(), duplicate.requestId());
    assertEquals(1, requested.replayGeneration());

    await(
        () ->
            replayUseCase.get(requested.requestId()).status()
                == ConnectorDeadLetterReplayStatus.REPLAYED,
        "durable replay publication");
    ConnectorDeadLetterRecord replayedDeadLetter = awaitRecord(key, 1);
    assertEquals(first.key(), replayedDeadLetter.key());
    assertEquals(first.value(), replayedDeadLetter.value());
    assertEquals(first.sourcePartition(), replayedDeadLetter.sourcePartition());
    assertEquals(first.sourceTimestamp(), replayedDeadLetter.sourceTimestamp());

    assertThrows(
        ConnectorDeadLetterReplayLimitExceededException.class,
        () ->
            replayUseCase.request(
                new RequestConnectorDeadLetterReplayCommand(
                    command.actor(), replayedDeadLetter.reference(), "retry again")));
    assertFalse(hasCommittedDltOffsets());
  }

  @Test
  void replaysAValidRetainedEnvelopeThroughTheExistingIdempotentInboxFlow() throws Exception {
    UUID eventId = UUID.randomUUID();
    UUID tenantId = UUID.fromString("00000000-0000-0000-0000-000000000010");
    UUID importRunId = UUID.fromString("00000000-0000-0000-0000-000000000082");
    String key = tenantId + ":IMPORT_RUN:" + importRunId;
    String value =
        """
        {
          "eventId":"%s",
          "eventType":"connector.import-run.completed.v1",
          "schemaVersion":1,
          "tenantId":"%s",
          "aggregateType":"IMPORT_RUN",
          "aggregateId":"%s",
          "payload":{
            "connectorId":"00000000-0000-0000-0000-000000000083",
            "importType":"CUSTOMERS",
            "importMode":"INCREMENTAL",
            "status":"COMPLETED",
            "fetchedCount":2,
            "acceptedCount":2,
            "rejectedCount":0,
            "duplicateCount":0,
            "attemptCount":1
          },
          "occurredAt":"%s"
        }
        """
            .formatted(eventId, tenantId, importRunId, SOURCE_TIMESTAMP);
    SendResult<String, String> sent =
        kafkaTemplate.send(directDltRecord(key, value, 77)).get(10, TimeUnit.SECONDS);
    ConnectorDeadLetterRecord deadLetter =
        inspectUseCase.get(
            new ConnectorDeadLetterReference(PARTITION, sent.getRecordMetadata().offset()));

    ConnectorDeadLetterReplayRequest requested =
        replayUseCase.request(
            new RequestConnectorDeadLetterReplayCommand(
                new ConnectorActor("https://issuer.example", "platform-admin-42"),
                deadLetter.reference(),
                "consumer contract is now compatible"));

    await(
        () ->
            jdbcTemplate.queryForObject(
                    "SELECT count(*) FROM connector_inbox_events WHERE event_id = ?",
                    Integer.class,
                    eventId)
                == 1,
        "replayed event in the durable inbox");
    await(
        () ->
            replayUseCase.get(requested.requestId()).status()
                == ConnectorDeadLetterReplayStatus.REPLAYED,
        "replay request completion");
    assertEquals(
        1,
        jdbcTemplate.queryForObject(
            "SELECT count(*) FROM connector_import_run_event_projection WHERE event_id = ?",
            Integer.class,
            eventId));
  }

  private ConnectorDeadLetterRecord awaitRecord(String key, int generation) throws Exception {
    Instant deadline = Instant.now().plusSeconds(20);
    while (Instant.now().isBefore(deadline)) {
      for (ConnectorDeadLetterRecord record : inspectUseCase.list(PARTITION, 0, 100).records()) {
        if (record.key().filter(key::equals).isPresent()
            && record.replayGeneration() == generation) {
          return record;
        }
      }
      Thread.sleep(100);
    }
    throw new AssertionError("Timed out waiting for connector DLT replay generation " + generation);
  }

  private boolean hasCommittedDltOffsets() throws Exception {
    try (AdminClient admin =
        AdminClient.create(
            Map.of(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers()))) {
      return admin
          .listConsumerGroupOffsets(GROUP_ID)
          .partitionsToOffsetAndMetadata()
          .get()
          .keySet()
          .stream()
          .anyMatch(partition -> partition.topic().equals(DLT_TOPIC));
    }
  }

  private static ProducerRecord<String, String> directDltRecord(
      String key, String value, long sourceOffset) {
    Headers headers =
        new RecordHeaders()
            .add(KafkaHeaders.DLT_ORIGINAL_TOPIC, bytes(SOURCE_TOPIC))
            .add(KafkaHeaders.DLT_ORIGINAL_PARTITION, integer(PARTITION))
            .add(KafkaHeaders.DLT_ORIGINAL_OFFSET, longValue(sourceOffset))
            .add(KafkaHeaders.DLT_ORIGINAL_TIMESTAMP, longValue(SOURCE_TIMESTAMP.toEpochMilli()))
            .add(KafkaHeaders.DLT_EXCEPTION_FQCN, bytes("java.lang.IllegalArgumentException"))
            .add(KafkaHeaders.DLT_EXCEPTION_MESSAGE, bytes("previous contract rejection"))
            .add(ConnectorKafkaHeaders.FAILURE_CODE, bytes("unsupported-connector-event-contract"))
            .add(ConnectorKafkaHeaders.RETRYABLE, bytes("false"));
    return new ProducerRecord<>(
        DLT_TOPIC, PARTITION, SOURCE_TIMESTAMP.toEpochMilli(), key, value, headers);
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

  private static void await(BooleanSupplier condition, String description) throws Exception {
    Instant deadline = Instant.now().plusSeconds(20);
    while (Instant.now().isBefore(deadline)) {
      if (condition.getAsBoolean()) {
        return;
      }
      Thread.sleep(100);
    }
    throw new AssertionError("Timed out waiting for " + description);
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class KafkaTopicsConfiguration {

    @Bean
    org.apache.kafka.clients.admin.NewTopic connectorRecoverySourceTopic() {
      return TopicBuilder.name(SOURCE_TOPIC).partitions(3).replicas(1).build();
    }

    @Bean
    org.apache.kafka.clients.admin.NewTopic connectorRecoveryDltTopic() {
      return TopicBuilder.name(DLT_TOPIC).partitions(3).replicas(1).build();
    }
  }
}
