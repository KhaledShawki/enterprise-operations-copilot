package io.github.khaledshawki.eoc.platform.connectormanagement.configuration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class ConnectorKafkaConsumerPropertiesTest {

  @Test
  void acceptsACompleteBoundedConsumerPolicy() {
    ConnectorKafkaConsumerProperties properties = properties();

    assertEquals("eoc-platform-connector-events-v1", properties.groupId());
    assertEquals("eoc.connector.integration-events.dlt", properties.dltTopic());
    assertEquals(4, properties.maxAttempts());
    assertEquals(Duration.ofSeconds(1), properties.retryBackoff());
    assertEquals(1_048_576, properties.maxEventBytes());
    assertEquals(3, properties.concurrency());
  }

  @Test
  void rejectsInvalidGroupAndDltTopicNames() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new ConnectorKafkaConsumerProperties(
                true,
                "bad group",
                "events.dlt",
                4,
                Duration.ofSeconds(1),
                Duration.ofSeconds(10),
                1024,
                1));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new ConnectorKafkaConsumerProperties(
                true,
                "group",
                "bad topic",
                4,
                Duration.ofSeconds(1),
                Duration.ofSeconds(10),
                1024,
                1));
  }

  @Test
  void rejectsUnboundedOrNonPositiveRuntimeSettings() {
    assertThrows(IllegalArgumentException.class, () -> withMaxAttempts(0));
    assertThrows(IllegalArgumentException.class, () -> withRetryBackoff(Duration.ofMillis(-1)));
    assertThrows(IllegalArgumentException.class, () -> withDltTimeout(Duration.ZERO));
    assertThrows(IllegalArgumentException.class, () -> withMaxEventBytes(0));
    assertThrows(IllegalArgumentException.class, () -> withConcurrency(0));
  }

  private static ConnectorKafkaConsumerProperties properties() {
    return new ConnectorKafkaConsumerProperties(
        true,
        "eoc-platform-connector-events-v1",
        "eoc.connector.integration-events.dlt",
        4,
        Duration.ofSeconds(1),
        Duration.ofSeconds(10),
        1_048_576,
        3);
  }

  private static ConnectorKafkaConsumerProperties withMaxAttempts(int value) {
    ConnectorKafkaConsumerProperties properties = properties();
    return new ConnectorKafkaConsumerProperties(
        true,
        properties.groupId(),
        properties.dltTopic(),
        value,
        properties.retryBackoff(),
        properties.dltSendTimeout(),
        properties.maxEventBytes(),
        properties.concurrency());
  }

  private static ConnectorKafkaConsumerProperties withRetryBackoff(Duration value) {
    ConnectorKafkaConsumerProperties properties = properties();
    return new ConnectorKafkaConsumerProperties(
        true,
        properties.groupId(),
        properties.dltTopic(),
        properties.maxAttempts(),
        value,
        properties.dltSendTimeout(),
        properties.maxEventBytes(),
        properties.concurrency());
  }

  private static ConnectorKafkaConsumerProperties withDltTimeout(Duration value) {
    ConnectorKafkaConsumerProperties properties = properties();
    return new ConnectorKafkaConsumerProperties(
        true,
        properties.groupId(),
        properties.dltTopic(),
        properties.maxAttempts(),
        properties.retryBackoff(),
        value,
        properties.maxEventBytes(),
        properties.concurrency());
  }

  private static ConnectorKafkaConsumerProperties withMaxEventBytes(int value) {
    ConnectorKafkaConsumerProperties properties = properties();
    return new ConnectorKafkaConsumerProperties(
        true,
        properties.groupId(),
        properties.dltTopic(),
        properties.maxAttempts(),
        properties.retryBackoff(),
        properties.dltSendTimeout(),
        value,
        properties.concurrency());
  }

  private static ConnectorKafkaConsumerProperties withConcurrency(int value) {
    ConnectorKafkaConsumerProperties properties = properties();
    return new ConnectorKafkaConsumerProperties(
        true,
        properties.groupId(),
        properties.dltTopic(),
        properties.maxAttempts(),
        properties.retryBackoff(),
        properties.dltSendTimeout(),
        properties.maxEventBytes(),
        value);
  }
}
