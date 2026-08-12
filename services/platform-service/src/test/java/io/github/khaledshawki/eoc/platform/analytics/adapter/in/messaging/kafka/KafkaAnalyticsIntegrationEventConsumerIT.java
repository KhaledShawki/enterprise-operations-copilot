package io.github.khaledshawki.eoc.platform.analytics.adapter.in.messaging.kafka;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import io.github.khaledshawki.eoc.analytics.application.port.in.ConsumeAnalyticsIntegrationEventUseCase;
import io.github.khaledshawki.eoc.platform.TestcontainersConfiguration;
import io.github.khaledshawki.eoc.platform.analytics.adapter.messaging.kafka.AnalyticsKafkaRecordKey;
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
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.serialization.StringDeserializer;
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
      "eoc.analytics-events.transport=kafka",
      "eoc.analytics-events.kafka.source-topic=eoc.analytics.operations.consumer-it",
      "eoc.analytics-events.kafka.consumer.enabled=true",
      "eoc.analytics-events.kafka.consumer.group-id=eoc-analytics-consumer-it",
      "eoc.analytics-events.kafka.consumer.dlt-topic=eoc.analytics.operations.consumer-it.dlt",
      "eoc.analytics-events.kafka.consumer.max-attempts=3",
      "eoc.analytics-events.kafka.consumer.retry-backoff=150ms",
      "eoc.analytics-events.kafka.consumer.dlt-send-timeout=5s",
      "eoc.analytics-events.kafka.consumer.concurrency=2",
      "eoc.connector-outbox.relay-enabled=false",
      "eoc.operations-outbox.relay-enabled=false",
      "spring.kafka.consumer.auto-offset-reset=earliest",
      "spring.kafka.admin.fail-fast=true"
    })
@Import({
  TestcontainersConfiguration.class,
  KafkaAnalyticsIntegrationEventConsumerIT.KafkaTopicsConfiguration.class
})
class KafkaAnalyticsIntegrationEventConsumerIT {

  private static final String SOURCE_TOPIC = "eoc.analytics.operations.consumer-it";
  private static final String DLT_TOPIC = SOURCE_TOPIC + ".dlt";
  private static final String GROUP_ID = "eoc-analytics-consumer-it";
  private static final UUID TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000010");
  private static final UUID PARTNER_ID = UUID.fromString("00000000-0000-0000-0000-000000000382");
  private static final UUID INVOICE_ID = UUID.fromString("00000000-0000-0000-0000-000000000383");
  private static final UUID CUSTOMER_ID = UUID.fromString("00000000-0000-0000-0000-000000000384");

  @Container static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka-native:4.2.1");

  @Autowired private KafkaTemplate<String, String> kafkaTemplate;
  @Autowired private JsonMapper jsonMapper;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private KafkaAnalyticsIntegrationEventDecoder decoder;
  @Autowired private ConsumeAnalyticsIntegrationEventUseCase consumeUseCase;

  @DynamicPropertySource
  static void kafkaProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
  }

  @BeforeEach
  void clearAnalyticsState() {
    jdbcTemplate.update("DELETE FROM analytics_business_partner_projections");
    jdbcTemplate.update("DELETE FROM analytics_invoice_receivable_projections");
    jdbcTemplate.update("DELETE FROM analytics_inbox_events");
  }

  @Test
  void commitsAfterDurableProjectionAndAbsorbsIdenticalReplay() throws Exception {
    String value = businessPartnerWire(UUID.randomUUID(), 1, "Acme AG");

    SentRecord first = send(value);
    await(
        () -> count("analytics_business_partner_projections") == 1, "business partner projection");
    awaitCommitted(first);

    SentRecord replay = send(value);
    awaitCommitted(replay);

    assertEquals(1, count("analytics_inbox_events"));
    assertEquals(1, count("analytics_business_partner_projections"));
  }

  @Test
  void routesMalformedEnvelopeToDltWithoutDurableEffect() throws Exception {
    SentRecord malformed = send("bad-key", "{not-json");

    ConsumerRecord<String, String> deadLetter =
        awaitDlt(record -> record.value().equals(malformed.value()));
    awaitCommitted(malformed);

    assertDeadLetter(
        deadLetter, malformed, KafkaAnalyticsIntegrationEventDecoder.INVALID_ENVELOPE, false);
    assertEquals(0, count("analytics_inbox_events"));
  }

  @Test
  void routesEventIdCollisionToDltWithoutChangingProjection() throws Exception {
    UUID eventId = UUID.randomUUID();
    SentRecord original = send(businessPartnerWire(eventId, 1, "Acme AG"));
    awaitCommitted(original);

    SentRecord collision = send(businessPartnerWire(eventId, 1, "Changed AG"));
    ConsumerRecord<String, String> deadLetter =
        awaitDlt(record -> record.value().equals(collision.value()));
    awaitCommitted(collision);

    assertDeadLetter(deadLetter, collision, "analytics-event-id-collision", false);
    assertEquals(
        "Acme AG",
        jdbcTemplate.queryForObject(
            """
            SELECT display_name
            FROM analytics_business_partner_projections
            WHERE tenant_id = ? AND business_partner_id = ?
            """,
            String.class,
            TENANT_ID,
            PARTNER_ID));
    assertEquals(1, count("analytics_inbox_events"));
  }

  @Test
  void deadLettersVersionGapThenAllowsMissingVersionToRepairStream() throws Exception {
    SentRecord first = send(invoiceWire(UUID.randomUUID(), 1, "0.00", "OPEN"));
    awaitCommitted(first);

    UUID gapEventId = UUID.randomUUID();
    SentRecord gap = send(invoiceWire(gapEventId, 3, "30.00", "PARTIALLY_PAID"));
    ConsumerRecord<String, String> deadLetter =
        awaitDlt(record -> record.value().equals(gap.value()));
    awaitCommitted(gap);

    assertDeadLetter(deadLetter, gap, KafkaAnalyticsIntegrationEventConsumer.VERSION_GAP, true);
    assertEquals(
        0,
        jdbcTemplate.queryForObject(
            "SELECT count(*) FROM analytics_inbox_events WHERE event_id = ?",
            Integer.class,
            gapEventId));

    SentRecord second = send(invoiceWire(UUID.randomUUID(), 2, "20.00", "PARTIALLY_PAID"));
    awaitCommitted(second);

    assertEquals(
        2L,
        jdbcTemplate.queryForObject(
            """
            SELECT aggregate_version
            FROM analytics_invoice_receivable_projections
            WHERE tenant_id = ? AND invoice_id = ?
            """,
            Long.class,
            TENANT_ID,
            INVOICE_ID));
    assertEquals(2, count("analytics_inbox_events"));
  }

  @Test
  void durablyAcceptsKnownPaymentWithoutDeadLetteringIt() throws Exception {
    SentRecord payment = send(paymentWire(UUID.randomUUID(), UUID.randomUUID(), 1));
    awaitCommitted(payment);

    assertEquals(1, count("analytics_inbox_events"));
    assertEquals(
        "IGNORED",
        jdbcTemplate.queryForObject(
            "SELECT projection_status FROM analytics_inbox_events", String.class));
    assertEquals(0, count("analytics_business_partner_projections"));
    assertEquals(0, count("analytics_invoice_receivable_projections"));
  }

  @Test
  void absorbsKafkaRedeliveryAfterDatabaseCommitBeforeOffsetCommit() throws Exception {
    String value = businessPartnerWire(UUID.randomUUID(), 1, "Acme AG");
    consumeUseCase.consume(decoder.decode(value));

    SentRecord redelivery = send(value);
    awaitCommitted(redelivery);

    assertEquals(1, count("analytics_inbox_events"));
    assertEquals(1, count("analytics_business_partner_projections"));
  }

  private SentRecord send(String value) throws Exception {
    var event = decoder.decode(value);
    return send(AnalyticsKafkaRecordKey.from(event), value);
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

  private void awaitCommitted(SentRecord sent) throws Exception {
    await(
        () -> committedOffset(sent.topicPartition()) > sent.offset(),
        "Kafka offset " + sent.offset());
  }

  private long committedOffset(TopicPartition partition) {
    try (AdminClient admin =
        AdminClient.create(
            Map.of(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers()))) {
      var offset =
          admin
              .listConsumerGroupOffsets(GROUP_ID)
              .partitionsToOffsetAndMetadata()
              .get()
              .get(partition);
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
    throw new AssertionError("Expected record was not published to the Analytics DLT");
  }

  private Map<String, Object> dltConsumerProperties() {
    Map<String, Object> properties = new HashMap<>();
    properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
    properties.put(ConsumerConfig.GROUP_ID_CONFIG, "analytics-dlt-reader-" + UUID.randomUUID());
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
        failureCode, header(deadLetter, KafkaAnalyticsConsumerConfiguration.FAILURE_CODE_HEADER));
    assertEquals(
        Boolean.toString(retryable),
        header(deadLetter, KafkaAnalyticsConsumerConfiguration.RETRYABLE_HEADER));
    assertNotNull(deadLetter.headers().lastHeader(KafkaHeaders.DLT_EXCEPTION_FQCN));
    assertNotNull(deadLetter.headers().lastHeader(KafkaHeaders.DLT_ORIGINAL_TOPIC));
    assertNotNull(deadLetter.headers().lastHeader(KafkaHeaders.DLT_ORIGINAL_OFFSET));
  }

  private static String header(ConsumerRecord<String, String> record, String name) {
    Header header = record.headers().lastHeader(name);
    assertNotNull(header, "Missing DLT header " + name);
    return new String(header.value(), StandardCharsets.UTF_8);
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

  private String businessPartnerWire(UUID eventId, long version, String displayName)
      throws Exception {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("businessPartnerId", PARTNER_ID.toString());
    payload.put("partnerNumber", "C-100");
    payload.put("displayName", displayName);
    payload.put("roles", List.of("CUSTOMER"));
    payload.put("source", source("BP-100", Long.toString(version)));

    return wire(
        eventId,
        "operations.business-partner.synchronized.v1",
        "BUSINESS_PARTNER",
        PARTNER_ID,
        version,
        payload);
  }

  private String invoiceWire(UUID eventId, long version, String paidAmount, String status)
      throws Exception {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("invoiceId", INVOICE_ID.toString());
    payload.put("customerId", CUSTOMER_ID.toString());
    payload.put("invoiceNumber", "INV-100");
    payload.put("originalAmount", money("100.00", "CHF"));
    payload.put("paidAmount", money(paidAmount, "CHF"));
    payload.put("issueDate", "2026-07-01");
    payload.put("dueDate", "2026-07-31");
    payload.put("cancelled", false);
    payload.put("status", status);
    payload.put("source", source("INV-100", Long.toString(version)));

    return wire(
        eventId, "operations.invoice.synchronized.v1", "INVOICE", INVOICE_ID, version, payload);
  }

  private String paymentWire(UUID eventId, UUID paymentId, long version) throws Exception {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("paymentId", paymentId.toString());
    payload.put("customerId", CUSTOMER_ID.toString());
    payload.put("amount", money("20.00", "CHF"));
    payload.put("paymentDate", "2026-08-11");
    payload.put("reversed", false);
    payload.put("status", "RECORDED");
    payload.put("source", source("PAY-100", Long.toString(version)));

    return wire(
        eventId, "operations.payment.synchronized.v1", "PAYMENT", paymentId, version, payload);
  }

  private String wire(
      UUID eventId,
      String eventType,
      String aggregateType,
      UUID aggregateId,
      long aggregateVersion,
      Map<String, Object> payload)
      throws Exception {
    Map<String, Object> wire = new LinkedHashMap<>();
    wire.put("eventId", eventId.toString());
    wire.put("eventType", eventType);
    wire.put("schemaVersion", 1);
    wire.put("tenantId", TENANT_ID.toString());
    wire.put("aggregateType", aggregateType);
    wire.put("aggregateId", aggregateId.toString());
    wire.put("aggregateVersion", aggregateVersion);
    wire.put("payload", payload);
    wire.put("occurredAt", "2026-08-11T21:00:00Z");
    return jsonMapper.writeValueAsString(wire);
  }

  private static Map<String, Object> source(String identity, String version) {
    Map<String, Object> source = new LinkedHashMap<>();
    source.put("sourceSystemId", "00000000-0000-0000-0000-000000000090");
    source.put("sourceIdentityKind", "SOURCE_RECORD_ID");
    source.put("sourceIdentity", identity);
    source.put("sourceVersion", version);
    source.put("sourceModifiedAt", null);
    return source;
  }

  private static Map<String, Object> money(String amount, String currency) {
    Map<String, Object> money = new LinkedHashMap<>();
    money.put("amount", new java.math.BigDecimal(amount));
    money.put("currency", currency);
    return money;
  }

  private record SentRecord(String key, String value, TopicPartition topicPartition, long offset) {}

  @TestConfiguration(proxyBeanMethods = false)
  static class KafkaTopicsConfiguration {

    @Bean
    org.apache.kafka.clients.admin.NewTopic analyticsSourceTopic() {
      return TopicBuilder.name(SOURCE_TOPIC).partitions(2).replicas(1).build();
    }

    @Bean
    org.apache.kafka.clients.admin.NewTopic analyticsDltTopic() {
      return TopicBuilder.name(DLT_TOPIC).partitions(2).replicas(1).build();
    }
  }
}
