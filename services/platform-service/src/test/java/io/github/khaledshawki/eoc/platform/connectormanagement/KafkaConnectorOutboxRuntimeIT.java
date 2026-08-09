package io.github.khaledshawki.eoc.platform.connectormanagement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.khaledshawki.eoc.connectormanagement.application.model.event.ConnectorIntegrationEventEnvelope;
import io.github.khaledshawki.eoc.connectormanagement.application.model.outbox.PublishConnectorOutboxBatchCommand;
import io.github.khaledshawki.eoc.connectormanagement.application.model.outbox.PublishConnectorOutboxBatchResult;
import io.github.khaledshawki.eoc.connectormanagement.application.port.in.ImportRunLifecycleUseCase;
import io.github.khaledshawki.eoc.connectormanagement.application.port.in.ImportRunReference;
import io.github.khaledshawki.eoc.connectormanagement.application.port.in.ImportRunResult;
import io.github.khaledshawki.eoc.connectormanagement.application.port.in.PublishConnectorOutboxBatchUseCase;
import io.github.khaledshawki.eoc.connectormanagement.application.port.in.RequestImportRunCommand;
import io.github.khaledshawki.eoc.connectormanagement.application.port.out.ConnectorIntegrationEventPublisher;
import io.github.khaledshawki.eoc.connectormanagement.application.port.out.ConnectorRepository;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.Connector;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ConnectorEndpoint;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ConnectorName;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ConnectorTenantId;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ConnectorType;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.CredentialReference;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ImportMode;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ImportStatus;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ImportType;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.SyncPolicy;
import io.github.khaledshawki.eoc.platform.TestcontainersConfiguration;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.ProducerFactory;
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
      "eoc.connector-events.kafka.topic=eoc.connector.integration-events.test",
      "eoc.connector-events.kafka.send-timeout=10s",
      "eoc.connector-outbox.relay-enabled=false",
      "spring.kafka.producer.properties[delivery.timeout.ms]=8000",
      "spring.kafka.producer.properties[request.timeout.ms]=3000"
    })
@Import({
  TestcontainersConfiguration.class,
  KafkaConnectorOutboxRuntimeIT.FixedClockConfiguration.class
})
class KafkaConnectorOutboxRuntimeIT {

  private static final String TOPIC = "eoc.connector.integration-events.test";
  private static final Instant NOW = Instant.parse("2026-08-09T19:00:00Z");
  private static final ConnectorTenantId TENANT_ID =
      ConnectorTenantId.of(UUID.fromString("00000000-0000-0000-0000-000000000010"));

  @Container static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka-native:4.2.1");

  @Autowired private ConnectorRepository connectorRepository;
  @Autowired private ImportRunLifecycleUseCase importRunLifecycleUseCase;
  @Autowired private PublishConnectorOutboxBatchUseCase publishConnectorOutboxBatchUseCase;
  @Autowired private ConnectorIntegrationEventPublisher eventPublisher;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private JsonMapper jsonMapper;
  @Autowired private ProducerFactory<String, String> producerFactory;

  @DynamicPropertySource
  static void kafkaProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
  }

  @BeforeAll
  static void createTopic() throws Exception {
    try (AdminClient admin =
        AdminClient.create(
            Map.of(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers()))) {
      admin.createTopics(List.of(new NewTopic(TOPIC, 3, (short) 1))).all().get();
    }
  }

  @BeforeEach
  void setUp() {
    jdbcTemplate.update("DELETE FROM connector_import_run_event_projection");
    jdbcTemplate.update("DELETE FROM connector_inbox_events");
    jdbcTemplate.update("DELETE FROM connector_outbox_events");
    jdbcTemplate.update("DELETE FROM connector_import_page_acceptances");
    jdbcTemplate.update("DELETE FROM connector_import_checkpoints");
    jdbcTemplate.update("DELETE FROM connector_import_runs");
    jdbcTemplate.update("DELETE FROM connectors");
  }

  @Test
  void shouldPublishTheOutboxEnvelopeToKafkaAndPreserveAtLeastOnceIdentity() throws Exception {
    Map<String, Object> producerConfiguration = producerFactory.getConfigurationProperties();
    assertEquals(
        "eoc-connector-outbox",
        String.valueOf(producerConfiguration.get(ProducerConfig.CLIENT_ID_CONFIG)));
    assertEquals("all", String.valueOf(producerConfiguration.get(ProducerConfig.ACKS_CONFIG)));
    assertEquals(
        "true",
        String.valueOf(producerConfiguration.get(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG)));
    assertEquals(
        5L,
        Long.parseLong(
            String.valueOf(
                producerConfiguration.get(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION))));
    assertEquals(
        5000L,
        Long.parseLong(
            String.valueOf(producerConfiguration.get(ProducerConfig.MAX_BLOCK_MS_CONFIG))));
    assertEquals(
        8000L,
        Long.parseLong(
            String.valueOf(producerConfiguration.get(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG))));
    assertEquals(
        3000L,
        Long.parseLong(
            String.valueOf(producerConfiguration.get(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG))));

    Connector connector = connectorRepository.save(activeConnector());
    ImportRunResult requested =
        importRunLifecycleUseCase.request(
            new RequestImportRunCommand(
                TENANT_ID.value(),
                connector.id().value(),
                ImportType.CUSTOMERS,
                ImportMode.INCREMENTAL));
    ImportRunReference reference =
        new ImportRunReference(TENANT_ID.value(), requested.importRunId().value());
    importRunLifecycleUseCase.start(reference);
    ImportRunResult completed = importRunLifecycleUseCase.complete(reference);

    assertEquals(ImportStatus.COMPLETED, completed.status());
    assertEquals(1, countByStatus("connector_outbox_events", "PENDING"));
    ConnectorIntegrationEventEnvelope envelope = persistedEnvelope();

    PublishConnectorOutboxBatchResult first =
        publishConnectorOutboxBatchUseCase.publishBatch(
            new PublishConnectorOutboxBatchCommand(
                "kafka-runtime-integration-worker", 10, Duration.ofSeconds(30)));
    PublishConnectorOutboxBatchResult replay =
        publishConnectorOutboxBatchUseCase.publishBatch(
            new PublishConnectorOutboxBatchCommand(
                "kafka-runtime-integration-worker", 10, Duration.ofSeconds(30)));

    assertEquals(new PublishConnectorOutboxBatchResult(1, 1, 0, 0), first);
    assertEquals(PublishConnectorOutboxBatchResult.empty(), replay);
    assertEquals(1, countByStatus("connector_outbox_events", "PUBLISHED"));
    assertEquals(0, count("connector_inbox_events"));
    assertEquals(0, count("connector_import_run_event_projection"));

    eventPublisher.publish(envelope);
    eventPublisher.publish(envelope);

    List<ConsumerRecord<String, String>> records = consumeEventCopies(envelope.eventId(), 3);
    assertEquals(3, records.size());
    String expectedKey =
        envelope.tenantId() + ":" + envelope.aggregateType() + ":" + envelope.aggregateId();
    for (ConsumerRecord<String, String> record : records) {
      assertEquals(expectedKey, record.key());
      assertEquals(envelope.occurredAt().toEpochMilli(), record.timestamp());
      assertWireEnvelope(record.value(), envelope);
    }
  }

  private void assertWireEnvelope(String value, ConnectorIntegrationEventEnvelope envelope)
      throws Exception {
    @SuppressWarnings("unchecked")
    Map<String, Object> wire = jsonMapper.readValue(value, Map.class);
    assertEquals(envelope.eventId().toString(), wire.get("eventId"));
    assertEquals(envelope.eventType(), wire.get("eventType"));
    assertEquals(envelope.schemaVersion(), ((Number) wire.get("schemaVersion")).intValue());
    assertEquals(envelope.tenantId().toString(), wire.get("tenantId"));
    assertEquals(envelope.aggregateType(), wire.get("aggregateType"));
    assertEquals(envelope.aggregateId().toString(), wire.get("aggregateId"));
    assertEquals(envelope.occurredAt().toString(), wire.get("occurredAt"));
    assertTrue(wire.get("payload") instanceof Map<?, ?>);
    assertFalse(wire.containsKey("publicationAttempt"));
    assertFalse(wire.containsKey("claimOwner"));
    assertFalse(wire.containsKey("claimedAt"));
  }

  private List<ConsumerRecord<String, String>> consumeEventCopies(UUID eventId, int expected)
      throws Exception {
    List<ConsumerRecord<String, String>> matches = new ArrayList<>();
    try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(consumerProperties())) {
      consumer.subscribe(List.of(TOPIC));
      Instant deadline = Instant.now().plusSeconds(15);
      while (matches.size() < expected && Instant.now().isBefore(deadline)) {
        ConsumerRecords<String, String> polled = consumer.poll(Duration.ofMillis(250));
        for (ConsumerRecord<String, String> record : polled) {
          @SuppressWarnings("unchecked")
          Map<String, Object> wire = jsonMapper.readValue(record.value(), Map.class);
          if (eventId.toString().equals(wire.get("eventId"))) {
            matches.add(record);
          }
        }
      }
    }
    return List.copyOf(matches);
  }

  private Map<String, Object> consumerProperties() {
    Map<String, Object> properties = new HashMap<>();
    properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
    properties.put(ConsumerConfig.GROUP_ID_CONFIG, "connector-outbox-it-" + UUID.randomUUID());
    properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
    properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
    properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
    properties.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
    return properties;
  }

  private ConnectorIntegrationEventEnvelope persistedEnvelope() {
    return jdbcTemplate.queryForObject(
        """
        SELECT event_id, event_type, schema_version, tenant_id, aggregate_type, aggregate_id,
               payload::text AS payload, occurred_at
        FROM connector_outbox_events
        """,
        KafkaConnectorOutboxRuntimeIT::mapEnvelope);
  }

  private static ConnectorIntegrationEventEnvelope mapEnvelope(ResultSet row, int rowNumber)
      throws SQLException {
    return new ConnectorIntegrationEventEnvelope(
        row.getObject("event_id", UUID.class),
        row.getString("event_type"),
        row.getInt("schema_version"),
        row.getObject("tenant_id", UUID.class),
        row.getString("aggregate_type"),
        row.getObject("aggregate_id", UUID.class),
        row.getString("payload"),
        row.getTimestamp("occurred_at").toInstant());
  }

  private int count(String table) {
    return jdbcTemplate.queryForObject("SELECT count(*) FROM " + table, Integer.class);
  }

  private int countByStatus(String table, String status) {
    return jdbcTemplate.queryForObject(
        "SELECT count(*) FROM " + table + " WHERE publish_status = ?", Integer.class, status);
  }

  private static Connector activeConnector() {
    Connector connector =
        Connector.create(
            TENANT_ID,
            ConnectorName.of("Kafka Runtime ERP"),
            ConnectorType.of("mock-erp"),
            ConnectorEndpoint.of("https://kafka-runtime.example.com/api"),
            CredentialReference.of(UUID.randomUUID()),
            SyncPolicy.manual());
    connector.activate();
    return connector;
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class FixedClockConfiguration {

    @Bean
    @Primary
    Clock connectorKafkaRuntimeClock() {
      return Clock.fixed(NOW, ZoneOffset.UTC);
    }
  }
}
