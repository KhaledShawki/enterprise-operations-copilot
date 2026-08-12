package io.github.khaledshawki.eoc.platform.connectormanagement.adapter.in.messaging.kafka;

import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.khaledshawki.eoc.platform.TestcontainersConfiguration;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
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
      "eoc.connector-events.kafka.topic=eoc.connector.integration-events.dlt-failure-it",
      "eoc.kafka.producer.max-block-timeout=1s",
      "eoc.connector-events.kafka.consumer.enabled=true",
      "eoc.connector-events.kafka.consumer.group-id=eoc-connector-dlt-failure-it",
      "eoc.connector-events.kafka.consumer.dlt-topic=eoc.connector.integration-events.dlt-failure-it.dlt",
      "eoc.connector-events.kafka.consumer.max-attempts=1",
      "eoc.connector-events.kafka.consumer.retry-backoff=0ms",
      "eoc.connector-events.kafka.consumer.dlt-send-timeout=1s",
      "eoc.connector-events.kafka.consumer.concurrency=1",
      "eoc.connector-outbox.relay-enabled=false",
      "spring.kafka.producer.properties[delivery.timeout.ms]=3000",
      "spring.kafka.producer.properties[request.timeout.ms]=1000",
      "spring.kafka.admin.fail-fast=true"
    })
@Import({
  TestcontainersConfiguration.class,
  KafkaConnectorDltPublicationFailureIT.SourceTopicConfiguration.class
})
class KafkaConnectorDltPublicationFailureIT {

  private static final String SOURCE_TOPIC = "eoc.connector.integration-events.dlt-failure-it";
  private static final String DLT_TOPIC = SOURCE_TOPIC + ".dlt";
  private static final String GROUP_ID = "eoc-connector-dlt-failure-it";

  @Container
  static final KafkaContainer KAFKA =
      new KafkaContainer("apache/kafka:4.2.1").withEnv("KAFKA_AUTO_CREATE_TOPICS_ENABLE", "false");

  @Autowired private KafkaTemplate<String, String> kafkaTemplate;

  @DynamicPropertySource
  static void kafkaProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
  }

  @Test
  void leavesTheSourceOffsetUncommittedUntilDltPublicationSucceeds() throws Exception {
    String value = "malformed-event-" + UUID.randomUUID();
    SendResult<String, String> result =
        kafkaTemplate
            .send(new ProducerRecord<>(SOURCE_TOPIC, "malformed-key", value))
            .get(10, TimeUnit.SECONDS);
    TopicPartition sourcePartition =
        new TopicPartition(SOURCE_TOPIC, result.getRecordMetadata().partition());
    long sourceOffset = result.getRecordMetadata().offset();

    Thread.sleep(4000);
    assertTrue(
        committedOffset(sourcePartition) <= sourceOffset,
        "the source record must not be acknowledged while the DLT is unavailable");

    createDltTopic();
    await(
        () -> committedOffset(sourcePartition) > sourceOffset, "source offset after DLT recovery");
    awaitDltValue(value);
  }

  private void createDltTopic() throws Exception {
    try (AdminClient admin = adminClient()) {
      admin.createTopics(List.of(new NewTopic(DLT_TOPIC, 2, (short) 1))).all().get();
    }
  }

  private long committedOffset(TopicPartition partition) {
    try (AdminClient admin = adminClient()) {
      var offsets = admin.listConsumerGroupOffsets(GROUP_ID).partitionsToOffsetAndMetadata().get();
      var offset = offsets.get(partition);
      return offset == null ? -1 : offset.offset();
    } catch (Exception exception) {
      return -1;
    }
  }

  private AdminClient adminClient() {
    return AdminClient.create(
        Map.of(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers()));
  }

  private void awaitDltValue(String expectedValue) {
    try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(dltConsumerProperties())) {
      consumer.subscribe(List.of(DLT_TOPIC));
      Instant deadline = Instant.now().plusSeconds(20);
      while (Instant.now().isBefore(deadline)) {
        ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(250));
        for (ConsumerRecord<String, String> record : records) {
          if (expectedValue.equals(record.value())) {
            return;
          }
        }
      }
    }
    throw new AssertionError("Recovered source record was not published to the DLT");
  }

  private Map<String, Object> dltConsumerProperties() {
    Map<String, Object> properties = new HashMap<>();
    properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
    properties.put(ConsumerConfig.GROUP_ID_CONFIG, "dlt-failure-reader-" + UUID.randomUUID());
    properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
    properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
    properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
    properties.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
    return properties;
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
  static class SourceTopicConfiguration {

    @Bean
    NewTopic connectorDltFailureSourceTopic() {
      return TopicBuilder.name(SOURCE_TOPIC).partitions(2).replicas(1).build();
    }
  }
}
