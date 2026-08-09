package io.github.khaledshawki.eoc.platform.connectormanagement.configuration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class ConnectorKafkaPropertiesTest {

  @Test
  void shouldAcceptKafkaSafeTopicAndPositiveTimeout() {
    ConnectorKafkaProperties properties =
        new ConnectorKafkaProperties(
            "eoc.connector.integration-events", Duration.ofSeconds(10), Duration.ofSeconds(5));

    assertEquals("eoc.connector.integration-events", properties.topic());
    assertEquals(Duration.ofSeconds(10), properties.sendTimeout());
    assertEquals(Duration.ofSeconds(5), properties.maxBlockTimeout());
  }

  @Test
  void shouldRejectInvalidTopicNames() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new ConnectorKafkaProperties(
                "bad topic", Duration.ofSeconds(10), Duration.ofSeconds(5)));
    assertThrows(
        IllegalArgumentException.class,
        () -> new ConnectorKafkaProperties(".", Duration.ofSeconds(10), Duration.ofSeconds(5)));
    assertThrows(
        IllegalArgumentException.class,
        () -> new ConnectorKafkaProperties("..", Duration.ofSeconds(10), Duration.ofSeconds(5)));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new ConnectorKafkaProperties(
                "a".repeat(250), Duration.ofSeconds(10), Duration.ofSeconds(5)));
  }

  @Test
  void shouldRejectNonPositiveSendTimeout() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new ConnectorKafkaProperties("events", Duration.ZERO, Duration.ofSeconds(5)));
    assertThrows(
        IllegalArgumentException.class,
        () -> new ConnectorKafkaProperties("events", Duration.ofMillis(-1), Duration.ofSeconds(5)));
  }

  @Test
  void shouldRejectNonPositiveMaxBlockTimeout() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new ConnectorKafkaProperties("events", Duration.ofSeconds(10), Duration.ZERO));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new ConnectorKafkaProperties("events", Duration.ofSeconds(10), Duration.ofMillis(-1)));
  }
}
