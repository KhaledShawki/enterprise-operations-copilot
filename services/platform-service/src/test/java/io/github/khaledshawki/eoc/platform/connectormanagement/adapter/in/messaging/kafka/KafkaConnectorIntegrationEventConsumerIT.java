package io.github.khaledshawki.eoc.platform.connectormanagement.adapter.in.messaging.kafka;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.khaledshawki.eoc.connectormanagement.application.model.event.ConnectorIntegrationEventEnvelope;
import io.github.khaledshawki.eoc.connectormanagement.application.model.event.ConnectorIntegrationEventType;
import io.github.khaledshawki.eoc.connectormanagement.application.port.in.ConsumeConnectorIntegrationEventUseCase;
import io.github.khaledshawki.eoc.platform.TestcontainersConfiguration;
import io.github.khaledshawki.eoc.platform.connectormanagement.adapter.messaging.kafka.ConnectorKafkaRecordKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.ListConsumerGroupOffsetsResult;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
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
import tools.jackson.databind.json.JsonMapper;

@Testcontainers
@SpringBootTest(
    properties = {
      "eoc.connector-events.transport=kafka",
      "eoc.connector-events.kafka.topic=eoc.connector.integration-events.consumer-it",
      "eoc.connector-events.kafka.consumer.enabled=true",
      "eoc.connector-events.kafka.consumer.group-id=eoc-connector-consumer-it",
      "eoc.connector-events.kafka.consumer.dlt-topic=eoc.connector.integration-events.consumer-it.dlt",
      "eoc.connector-events.kafka.consumer.max-attempts=3",
      "eoc.connector-events.kafka.consumer.retry-backoff=200ms",
      "eoc.connector-events.kafka.consumer.dlt-send-timeout=5s",
      "eoc.connector-events.kafka.consumer.concurrency=2",
      "eoc.connector-outbox.relay-enabled=false",
      "spring.kafka.consumer.auto-offset-reset=earliest",
      "spring.kafka.admin.fail-fast=true"
    })
@Import({
  TestcontainersConfiguration.class,
  KafkaConnectorIntegrationEventConsumerIT.KafkaTopicsConfiguration.class
})
class KafkaConnectorIntegrationEventConsumerIT {

  private static final String SOURCE_TOPIC = "eoc.connector.integration-events.consumer-it";
  private static final String DLT_TOPIC = SOURCE_TOPIC + ".dlt";
  private static final String GROUP_ID = "eoc-connector-consumer-it";
  private static final UUID TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000010");
  private static final UUID IMPORT_RUN_ID = UUID.fromString("00000000-0000-0000-0000-000000000082");
  private static final Instant OCCURRED_AT = Instant.parse("2026-08-09T19:00:00Z");
  private static final String TRIGGER_NAME = "connector_kafka_retry_gate_trigger";
  private static final String FUNCTION_NAME = "connector_kafka_retry_gate_function";
  private static final String SEQUENCE_NAME = "connector_kafka_retry_attempt_sequence";

  @Container static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka-native:4.2.1");

  @Autowired private KafkaTemplate<String, String> kafkaTemplate;
  @Autowired private JsonMapper jsonMapper;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private ConsumeConnectorIntegrationEventUseCase consumeUseCase;

  @DynamicPropertySource
  static void kafkaProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
  }

  @BeforeEach
  void setUp() {
    removeRetryGateInfrastructure();
    jdbcTemplate.update("DELETE FROM connector_import_run_event_projection");
    jdbcTemplate.update("DELETE FROM connector_inbox_events");
    createRetryGateInfrastructure();
  }

  @AfterEach
  void tearDown() {
    removeRetryGateInfrastructure();
  }

  @Test
  void commitsAfterDurableProcessingAndAbsorbsAnIdenticalReplay() throws Exception {
    ConnectorIntegrationEventEnvelope event = completedEvent(UUID.randomUUID(), 2);

    SentRecord first = send(event);
    await(() -> count("connector_inbox_events") == 1, "first inbox write");
    awaitCommitted(first);
    SentRecord replay = send(event);
    awaitCommitted(replay);

    assertEquals(1, count("connector_inbox_events"));
    assertEquals(1, count("connector_import_run_event_projection"));
  }

  @Test
  void routesEventIdCollisionsToTheDltWithoutCorruptingTheProjection() throws Exception {
    UUID eventId = UUID.randomUUID();
    SentRecord original = send(completedEvent(eventId, 2));
    awaitCommitted(original);

    SentRecord collision = send(completedEvent(eventId, 3));
    ConsumerRecord<String, String> deadLetter =
        awaitDlt(record -> record.value().equals(collision.value()));
    awaitCommitted(collision);

    assertDeadLetter(deadLetter, collision, "connector-event-id-collision", false);
    assertEquals(1, count("connector_inbox_events"));
    assertEquals(1, count("connector_import_run_event_projection"));
  }

  @Test
  void routesMalformedEnvelopesToTheDltWithoutCallingTheApplication() throws Exception {
    SentRecord malformed = send("malformed-key", "{\"testMarker\":\"" + UUID.randomUUID() + "\"");

    ConsumerRecord<String, String> deadLetter =
        awaitDlt(record -> record.value().equals(malformed.value()));
    awaitCommitted(malformed);

    assertDeadLetter(
        deadLetter, malformed, KafkaConnectorIntegrationEventDecoder.INVALID_ENVELOPE, false);
    assertEquals(0, count("connector_inbox_events"));
    assertEquals(0, count("connector_import_run_event_projection"));
  }

  @Test
  void routesUnsupportedContractsToTheDltWithoutRetryingThem() throws Exception {
    ConnectorIntegrationEventEnvelope supported = completedEvent(UUID.randomUUID(), 2);
    ConnectorIntegrationEventEnvelope unsupported =
        new ConnectorIntegrationEventEnvelope(
            supported.eventId(),
            "connector.import-run.completed.v2",
            2,
            supported.tenantId(),
            supported.aggregateType(),
            supported.aggregateId(),
            supported.payload(),
            supported.occurredAt());

    SentRecord sent = send(unsupported);
    ConsumerRecord<String, String> deadLetter =
        awaitDlt(record -> record.value().equals(sent.value()));
    awaitCommitted(sent);

    assertDeadLetter(deadLetter, sent, "unsupported-connector-event-contract", false);
    assertEquals(0, count("connector_inbox_events"));
    assertEquals(0, count("connector_import_run_event_projection"));
  }

  @Test
  void retriesTemporaryPostgresFailureThenCommitsOneDurableEffect() throws Exception {
    ConnectorIntegrationEventEnvelope event = completedEvent(UUID.randomUUID(), 2);
    addRetryGate(event.eventId());

    SentRecord sent = send(event);
    await(() -> retryAttempts() >= 1, "first retryable database failure");
    removeRetryGate(event.eventId());
    await(() -> count("connector_inbox_events") == 1, "event after retry gate removal");
    awaitCommitted(sent);

    assertTrue(retryAttempts() >= 1);
    assertEquals(1, count("connector_inbox_events"));
    assertEquals(1, count("connector_import_run_event_projection"));
  }

  @Test
  void boundsPersistentPostgresRetriesAndLetsTheSourcePartitionContinue() throws Exception {
    ConnectorIntegrationEventEnvelope failing = completedEvent(UUID.randomUUID(), 2);
    addRetryGate(failing.eventId());
    ConnectorIntegrationEventEnvelope following = completedEvent(UUID.randomUUID(), 2);

    SentRecord failed = send(failing);
    SentRecord next = send(following);
    ConsumerRecord<String, String> deadLetter =
        awaitDlt(record -> record.value().equals(failed.value()));
    await(() -> inboxContains(following.eventId()), "following source-partition event");
    awaitCommitted(next);

    assertDeadLetter(deadLetter, failed, "connector-inbox-unavailable", true);
    assertEquals(3L, retryAttempts());
    assertEquals(1, count("connector_inbox_events"));
    assertEquals(1, count("connector_import_run_event_projection"));
  }

  @Test
  void absorbsRedeliveryAfterDatabaseCommitBeforeOffsetCommit() throws Exception {
    ConnectorIntegrationEventEnvelope event = completedEvent(UUID.randomUUID(), 2);
    consumeUseCase.consume(event);

    SentRecord redelivery = send(event);
    awaitCommitted(redelivery);

    assertEquals(1, count("connector_inbox_events"));
    assertEquals(1, count("connector_import_run_event_projection"));
  }

  private SentRecord send(ConnectorIntegrationEventEnvelope event) throws Exception {
    return send(ConnectorKafkaRecordKey.from(event), wireValue(event));
  }

  private SentRecord send(String key, String value) throws Exception {
    SendResult<String, String> result =
        kafkaTemplate
            .send(new ProducerRecord<>(SOURCE_TOPIC, null, System.currentTimeMillis(), key, value))
            .get(10, TimeUnit.SECONDS);
    return new SentRecord(
        key,
        value,
        new TopicPartition(SOURCE_TOPIC, result.getRecordMetadata().partition()),
        result.getRecordMetadata().offset());
  }

  private String wireValue(ConnectorIntegrationEventEnvelope event) throws Exception {
    @SuppressWarnings("unchecked")
    Map<String, Object> payload = jsonMapper.readValue(event.payload(), Map.class);
    Map<String, Object> wire = new LinkedHashMap<>();
    wire.put("eventId", event.eventId().toString());
    wire.put("eventType", event.eventType());
    wire.put("schemaVersion", event.schemaVersion());
    wire.put("tenantId", event.tenantId().toString());
    wire.put("aggregateType", event.aggregateType());
    wire.put("aggregateId", event.aggregateId().toString());
    wire.put("payload", payload);
    wire.put("occurredAt", event.occurredAt().toString());
    return jsonMapper.writeValueAsString(wire);
  }

  private void awaitCommitted(SentRecord sent) throws Exception {
    await(
        () -> committedOffset(sent.topicPartition()) > sent.offset(),
        "Kafka offset " + sent.offset());
  }

  private long committedOffset(TopicPartition partition) {
    try (AdminClient admin =
        AdminClient.create(
            Map.of(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers()))) {
      ListConsumerGroupOffsetsResult result = admin.listConsumerGroupOffsets(GROUP_ID);
      var offset = result.partitionsToOffsetAndMetadata().get().get(partition);
      return offset == null ? -1 : offset.offset();
    } catch (Exception exception) {
      return -1;
    }
  }

  private ConsumerRecord<String, String> awaitDlt(Predicate<ConsumerRecord<String, String>> match)
      throws Exception {
    try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(dltConsumerProperties())) {
      consumer.subscribe(List.of(DLT_TOPIC));
      Instant deadline = Instant.now().plusSeconds(20);
      while (Instant.now().isBefore(deadline)) {
        ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(250));
        for (ConsumerRecord<String, String> record : records) {
          if (match.test(record)) {
            return record;
          }
        }
      }
    }
    throw new AssertionError("Expected record was not published to the connector DLT");
  }

  private Map<String, Object> dltConsumerProperties() {
    Map<String, Object> properties = new HashMap<>();
    properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
    properties.put(ConsumerConfig.GROUP_ID_CONFIG, "connector-dlt-reader-" + UUID.randomUUID());
    properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
    properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
    properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
    properties.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
    return properties;
  }

  private void assertDeadLetter(
      ConsumerRecord<String, String> deadLetter,
      SentRecord source,
      String failureCode,
      boolean retryable) {
    assertEquals(source.key(), deadLetter.key());
    assertEquals(source.value(), deadLetter.value());
    assertEquals(source.topicPartition().partition(), deadLetter.partition());
    assertEquals(
        failureCode, header(deadLetter, KafkaConnectorConsumerConfiguration.FAILURE_CODE_HEADER));
    assertEquals(
        Boolean.toString(retryable),
        header(deadLetter, KafkaConnectorConsumerConfiguration.RETRYABLE_HEADER));
    assertNotNull(deadLetter.headers().lastHeader(KafkaHeaders.DLT_EXCEPTION_FQCN));
    assertNotNull(deadLetter.headers().lastHeader(KafkaHeaders.DLT_ORIGINAL_TOPIC));
    assertNotNull(deadLetter.headers().lastHeader(KafkaHeaders.DLT_ORIGINAL_OFFSET));
  }

  private static String header(ConsumerRecord<String, String> record, String name) {
    Header header = record.headers().lastHeader(name);
    assertNotNull(header, "Missing DLT header " + name);
    return new String(header.value(), StandardCharsets.UTF_8);
  }

  private void createRetryGateInfrastructure() {
    jdbcTemplate.execute("CREATE SEQUENCE " + SEQUENCE_NAME + " START WITH 1");
    jdbcTemplate.execute("CREATE TABLE connector_kafka_retry_gate (event_id uuid PRIMARY KEY)");
    jdbcTemplate.execute(
        """
        CREATE OR REPLACE FUNCTION connector_kafka_retry_gate_function()
        RETURNS trigger
        LANGUAGE plpgsql
        AS $$
        BEGIN
          IF EXISTS (
            SELECT 1 FROM connector_kafka_retry_gate WHERE event_id = NEW.event_id
          ) THEN
            PERFORM nextval('connector_kafka_retry_attempt_sequence');
            RAISE EXCEPTION 'forced retryable connector projection failure';
          END IF;
          RETURN NEW;
        END;
        $$
        """);
    jdbcTemplate.execute(
        """
        CREATE TRIGGER connector_kafka_retry_gate_trigger
        BEFORE INSERT ON connector_import_run_event_projection
        FOR EACH ROW
        EXECUTE FUNCTION connector_kafka_retry_gate_function()
        """);
  }

  private void removeRetryGateInfrastructure() {
    jdbcTemplate.execute(
        "DROP TRIGGER IF EXISTS " + TRIGGER_NAME + " ON connector_import_run_event_projection");
    jdbcTemplate.execute("DROP FUNCTION IF EXISTS " + FUNCTION_NAME + "()");
    jdbcTemplate.execute("DROP TABLE IF EXISTS connector_kafka_retry_gate");
    jdbcTemplate.execute("DROP SEQUENCE IF EXISTS " + SEQUENCE_NAME);
  }

  private void addRetryGate(UUID eventId) {
    jdbcTemplate.update("INSERT INTO connector_kafka_retry_gate (event_id) VALUES (?)", eventId);
  }

  private void removeRetryGate(UUID eventId) {
    jdbcTemplate.update("DELETE FROM connector_kafka_retry_gate WHERE event_id = ?", eventId);
  }

  private long retryAttempts() {
    return jdbcTemplate.queryForObject(
        "SELECT CASE WHEN is_called THEN last_value ELSE 0 END FROM " + SEQUENCE_NAME, Long.class);
  }

  private boolean inboxContains(UUID eventId) {
    return jdbcTemplate.queryForObject(
            "SELECT count(*) FROM connector_inbox_events WHERE event_id = ?",
            Integer.class,
            eventId)
        == 1;
  }

  private int count(String table) {
    return jdbcTemplate.queryForObject("SELECT count(*) FROM " + table, Integer.class);
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

  private static ConnectorIntegrationEventEnvelope completedEvent(UUID eventId, int accepted) {
    return new ConnectorIntegrationEventEnvelope(
        eventId,
        ConnectorIntegrationEventType.IMPORT_RUN_COMPLETED.eventType(),
        ConnectorIntegrationEventType.IMPORT_RUN_COMPLETED.schemaVersion(),
        TENANT_ID,
        ConnectorIntegrationEventType.IMPORT_RUN_COMPLETED.aggregateType(),
        IMPORT_RUN_ID,
        completedPayload(accepted),
        OCCURRED_AT);
  }

  private static String completedPayload(int accepted) {
    return """
    {
      "connectorId": "00000000-0000-0000-0000-000000000083",
      "importType": "CUSTOMERS",
      "importMode": "INCREMENTAL",
      "status": "COMPLETED",
      "fetchedCount": %d,
      "acceptedCount": %d,
      "rejectedCount": 0,
      "duplicateCount": 0,
      "attemptCount": 1
    }
    """
        .formatted(accepted, accepted);
  }

  private record SentRecord(String key, String value, TopicPartition topicPartition, long offset) {}

  @TestConfiguration(proxyBeanMethods = false)
  static class KafkaTopicsConfiguration {

    @Bean
    org.apache.kafka.clients.admin.NewTopic connectorConsumerSourceTopic() {
      return TopicBuilder.name(SOURCE_TOPIC).partitions(4).replicas(1).build();
    }

    @Bean
    org.apache.kafka.clients.admin.NewTopic connectorConsumerDltTopic() {
      return TopicBuilder.name(DLT_TOPIC).partitions(4).replicas(1).build();
    }
  }
}
