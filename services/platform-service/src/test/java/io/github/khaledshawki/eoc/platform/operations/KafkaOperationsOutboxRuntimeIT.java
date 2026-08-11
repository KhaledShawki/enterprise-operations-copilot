package io.github.khaledshawki.eoc.platform.operations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.khaledshawki.eoc.operations.application.model.event.InvoiceSynchronizedPayload;
import io.github.khaledshawki.eoc.operations.application.model.event.OperationsIntegrationEvent;
import io.github.khaledshawki.eoc.operations.application.model.event.OperationsIntegrationEventType;
import io.github.khaledshawki.eoc.operations.application.model.event.OperationsMoneyPayload;
import io.github.khaledshawki.eoc.operations.application.model.event.PendingOperationsIntegrationEvent;
import io.github.khaledshawki.eoc.operations.application.model.event.SourceRecordEvidence;
import io.github.khaledshawki.eoc.operations.application.model.outbox.ClaimedOperationsOutboxEvent;
import io.github.khaledshawki.eoc.operations.application.model.outbox.OperationsOutboxClaim;
import io.github.khaledshawki.eoc.operations.application.model.outbox.OperationsOutboxPublicationFailure;
import io.github.khaledshawki.eoc.operations.application.model.outbox.OperationsOutboxPublicationPolicy;
import io.github.khaledshawki.eoc.operations.application.model.outbox.OperationsOutboxPublicationRetry;
import io.github.khaledshawki.eoc.operations.application.model.outbox.OperationsOutboxPublicationSuccess;
import io.github.khaledshawki.eoc.operations.application.model.outbox.PublishOperationsOutboxBatchCommand;
import io.github.khaledshawki.eoc.operations.application.model.outbox.PublishOperationsOutboxBatchResult;
import io.github.khaledshawki.eoc.operations.application.port.in.PublishOperationsOutboxBatchUseCase;
import io.github.khaledshawki.eoc.operations.application.port.out.OperationsIntegrationEventOutbox;
import io.github.khaledshawki.eoc.operations.application.port.out.OperationsIntegrationEventPublisher;
import io.github.khaledshawki.eoc.operations.application.port.out.OperationsOutboxRepository;
import io.github.khaledshawki.eoc.operations.application.service.PublishOperationsOutboxBatchService;
import io.github.khaledshawki.eoc.platform.TestcontainersConfiguration;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import tools.jackson.databind.json.JsonMapper;

@Testcontainers
@SpringBootTest(
    properties = {
      "eoc.operations-events.transport=kafka",
      "eoc.operations-events.kafka.topic=eoc.operations.integration-events.test",
      "eoc.operations-events.kafka.send-timeout=10s",
      "eoc.operations-outbox.relay-enabled=true",
      "eoc.operations-outbox.batch-size=1",
      "eoc.operations-outbox.claim-lease=30s",
      "eoc.operations-outbox.initial-delay-ms=3600000",
      "spring.kafka.producer.properties[delivery.timeout.ms]=8000",
      "spring.kafka.producer.properties[request.timeout.ms]=3000"
    })
@Import({
  TestcontainersConfiguration.class,
  KafkaOperationsOutboxRuntimeIT.MutableClockConfiguration.class
})
class KafkaOperationsOutboxRuntimeIT {

  private static final String TOPIC = "eoc.operations.integration-events.test";
  private static final Instant NOW = Instant.parse("2026-08-11T13:30:00Z");
  private static final Duration CLAIM_LEASE = Duration.ofSeconds(30);
  private static final UUID TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000701");
  private static final UUID CUSTOMER_ID = UUID.fromString("00000000-0000-0000-0000-000000000702");
  private static final UUID INVOICE_ID = UUID.fromString("00000000-0000-0000-0000-000000000703");
  private static final UUID SOURCE_SYSTEM_ID =
      UUID.fromString("00000000-0000-0000-0000-000000000704");

  @Container static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka-native:4.2.1");

  @Autowired private OperationsIntegrationEventOutbox eventOutbox;
  @Autowired private OperationsOutboxRepository outboxRepository;
  @Autowired private OperationsIntegrationEventPublisher eventPublisher;
  @Autowired private PublishOperationsOutboxBatchUseCase publishOutboxBatchUseCase;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private JsonMapper jsonMapper;
  @Autowired private MutableClock clock;
  @Autowired private PlatformTransactionManager transactionManager;
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
    clock.set(NOW);
    jdbcTemplate.update("DELETE FROM operations_outbox_events");
    jdbcTemplate.update("DELETE FROM operations_event_stream_versions");
  }

  @Test
  void shouldPublishTheDurableOperationsEnvelopeOnlyAfterKafkaAcknowledges() throws Exception {
    assertProducerSafetyConfiguration();
    OperationsIntegrationEvent event = append(invoiceEvent("INV-701"));

    assertEquals(1, countByStatus("PENDING"));
    PublishOperationsOutboxBatchResult result =
        publishOutboxBatchUseCase.publishBatch(command("operations-kafka-runtime-worker"));

    assertEquals(new PublishOperationsOutboxBatchResult(1, 1, 0, 0), result);
    assertEquals(1, countByStatus("PUBLISHED"));
    ConsumerRecord<String, String> record = consumeEventCopies(event.eventId(), 1).getFirst();
    assertRecord(record, event);
  }

  @Test
  void shouldRepublishTheSameIdentityAfterKafkaAckWhenTheDatabaseMarkerWasNotWritten()
      throws Exception {
    OperationsIntegrationEvent event = append(invoiceEvent("INV-702"));
    OperationsOutboxRepository failFirstPublishedMarker =
        new FailFirstPublishedMarkerRepository(outboxRepository);
    PublishOperationsOutboxBatchUseCase crashWindowService =
        new PublishOperationsOutboxBatchService(
            failFirstPublishedMarker,
            eventPublisher,
            new OperationsOutboxPublicationPolicy(5, Duration.ofSeconds(1)),
            clock);

    assertThrows(
        SimulatedProcessFailure.class,
        () -> crashWindowService.publishBatch(command("operations-kafka-crash-worker")));
    assertEquals(1, countByStatus("CLAIMED"));

    clock.advance(CLAIM_LEASE.plusSeconds(1));
    PublishOperationsOutboxBatchResult recovered =
        crashWindowService.publishBatch(command("operations-kafka-recovery-worker"));

    assertEquals(new PublishOperationsOutboxBatchResult(1, 1, 0, 0), recovered);
    assertEquals(1, countByStatus("PUBLISHED"));
    assertEquals(
        2,
        jdbcTemplate.queryForObject(
            "SELECT publish_attempt_count FROM operations_outbox_events WHERE event_id = ?",
            Integer.class,
            event.eventId()));

    List<ConsumerRecord<String, String>> copies = consumeEventCopies(event.eventId(), 2);
    assertEquals(2, copies.size());
    assertRecord(copies.get(0), event);
    assertRecord(copies.get(1), event);
    assertEquals(copies.get(0).key(), copies.get(1).key());
    assertEquals(copies.get(0).value(), copies.get(1).value());
  }

  private void assertProducerSafetyConfiguration() {
    Map<String, Object> configuration = producerFactory.getConfigurationProperties();
    assertEquals(
        "eoc-platform-outbox", String.valueOf(configuration.get(ProducerConfig.CLIENT_ID_CONFIG)));
    assertEquals("all", String.valueOf(configuration.get(ProducerConfig.ACKS_CONFIG)));
    assertEquals(
        "true", String.valueOf(configuration.get(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG)));
    assertEquals(
        5L,
        Long.parseLong(
            String.valueOf(
                configuration.get(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION))));
    assertEquals(
        5000L,
        Long.parseLong(String.valueOf(configuration.get(ProducerConfig.MAX_BLOCK_MS_CONFIG))));
    assertEquals(
        8000L,
        Long.parseLong(
            String.valueOf(configuration.get(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG))));
    assertEquals(
        3000L,
        Long.parseLong(
            String.valueOf(configuration.get(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG))));
  }

  private void assertRecord(ConsumerRecord<String, String> record, OperationsIntegrationEvent event)
      throws Exception {
    assertEquals(
        event.tenantId() + ":" + event.aggregateType() + ":" + event.aggregateId(), record.key());
    assertEquals(event.occurredAt().toEpochMilli(), record.timestamp());

    @SuppressWarnings("unchecked")
    Map<String, Object> wire = jsonMapper.readValue(record.value(), Map.class);
    assertEquals(event.eventId().toString(), wire.get("eventId"));
    assertEquals(event.eventType(), wire.get("eventType"));
    assertEquals(event.schemaVersion(), ((Number) wire.get("schemaVersion")).intValue());
    assertEquals(event.tenantId().toString(), wire.get("tenantId"));
    assertEquals(event.aggregateType(), wire.get("aggregateType"));
    assertEquals(event.aggregateId().toString(), wire.get("aggregateId"));
    assertEquals(event.aggregateVersion(), ((Number) wire.get("aggregateVersion")).longValue());
    assertEquals(event.occurredAt().toString(), wire.get("occurredAt"));
    assertTrue(wire.get("payload") instanceof Map<?, ?>);
    assertFalse(wire.containsKey("publicationAttempt"));
    assertFalse(wire.containsKey("claimOwner"));
    assertFalse(wire.containsKey("claimedAt"));
  }

  private OperationsIntegrationEvent append(PendingOperationsIntegrationEvent pending) {
    OperationsIntegrationEvent appended =
        new TransactionTemplate(transactionManager).execute(status -> eventOutbox.append(pending));
    return Objects.requireNonNull(appended, "Operations outbox transaction returned null");
  }

  private PendingOperationsIntegrationEvent invoiceEvent(String invoiceNumber) {
    return new PendingOperationsIntegrationEvent(
        OperationsIntegrationEventType.INVOICE_SYNCHRONIZED,
        TENANT_ID,
        clock.instant().minusSeconds(1),
        new InvoiceSynchronizedPayload(
            INVOICE_ID,
            CUSTOMER_ID,
            invoiceNumber,
            new OperationsMoneyPayload(new BigDecimal("100.00"), "EUR"),
            new OperationsMoneyPayload(new BigDecimal("0.00"), "EUR"),
            LocalDate.of(2026, 8, 1),
            LocalDate.of(2026, 8, 31),
            false,
            "OPEN",
            new SourceRecordEvidence(
                SOURCE_SYSTEM_ID,
                "SOURCE_RECORD_ID",
                "source-" + invoiceNumber,
                "v1",
                Optional.empty())));
  }

  private static PublishOperationsOutboxBatchCommand command(String workerId) {
    return new PublishOperationsOutboxBatchCommand(workerId, 1, CLAIM_LEASE);
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
    properties.put(ConsumerConfig.GROUP_ID_CONFIG, "operations-outbox-it-" + UUID.randomUUID());
    properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
    properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
    properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
    properties.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
    return properties;
  }

  private int countByStatus(String status) {
    return jdbcTemplate.queryForObject(
        "SELECT count(*) FROM operations_outbox_events WHERE publish_status = ?",
        Integer.class,
        status);
  }

  private static final class FailFirstPublishedMarkerRepository
      implements OperationsOutboxRepository {

    private final OperationsOutboxRepository delegate;
    private boolean failNext = true;

    private FailFirstPublishedMarkerRepository(OperationsOutboxRepository delegate) {
      this.delegate = delegate;
    }

    @Override
    public List<ClaimedOperationsOutboxEvent> claimPublishable(OperationsOutboxClaim claim) {
      return delegate.claimPublishable(claim);
    }

    @Override
    public void markPublished(OperationsOutboxPublicationSuccess success) {
      if (failNext) {
        failNext = false;
        throw new SimulatedProcessFailure();
      }
      delegate.markPublished(success);
    }

    @Override
    public void scheduleRetry(OperationsOutboxPublicationRetry retry) {
      delegate.scheduleRetry(retry);
    }

    @Override
    public void markFailed(OperationsOutboxPublicationFailure failure) {
      delegate.markFailed(failure);
    }
  }

  private static final class SimulatedProcessFailure extends RuntimeException {}

  static final class MutableClock extends Clock {

    private Instant current;

    private MutableClock(Instant current) {
      this.current = current;
    }

    void set(Instant instant) {
      current = instant;
    }

    void advance(Duration duration) {
      current = current.plus(duration);
    }

    @Override
    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return current;
    }
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class MutableClockConfiguration {

    @Bean
    @Primary
    MutableClock operationsKafkaRuntimeClock() {
      return new MutableClock(NOW);
    }
  }
}
