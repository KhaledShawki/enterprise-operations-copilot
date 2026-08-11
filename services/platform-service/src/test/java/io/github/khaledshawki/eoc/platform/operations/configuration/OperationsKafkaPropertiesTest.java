package io.github.khaledshawki.eoc.platform.operations.configuration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class OperationsKafkaPropertiesTest {

  @Test
  void acceptsKafkaSafeTopicAndBoundedTimeout() {
    OperationsKafkaProperties properties =
        new OperationsKafkaProperties("eoc.operations.integration-events", Duration.ofSeconds(10));

    assertEquals("eoc.operations.integration-events", properties.topic());
    assertEquals(Duration.ofSeconds(10), properties.sendTimeout());
  }

  @Test
  void rejectsInvalidTopics() {
    assertThrows(
        NullPointerException.class,
        () -> new OperationsKafkaProperties(null, Duration.ofSeconds(10)));
    assertThrows(
        IllegalArgumentException.class,
        () -> new OperationsKafkaProperties("bad topic", Duration.ofSeconds(10)));
    assertThrows(
        IllegalArgumentException.class,
        () -> new OperationsKafkaProperties(".", Duration.ofSeconds(10)));
    assertThrows(
        IllegalArgumentException.class,
        () -> new OperationsKafkaProperties("..", Duration.ofSeconds(10)));
    assertThrows(
        IllegalArgumentException.class,
        () -> new OperationsKafkaProperties("a".repeat(250), Duration.ofSeconds(10)));
  }

  @Test
  void rejectsInvalidSendTimeouts() {
    assertThrows(NullPointerException.class, () -> new OperationsKafkaProperties("events", null));
    assertThrows(
        IllegalArgumentException.class,
        () -> new OperationsKafkaProperties("events", Duration.ZERO));
    assertThrows(
        IllegalArgumentException.class,
        () -> new OperationsKafkaProperties("events", Duration.ofMillis(-1)));
    assertThrows(
        IllegalArgumentException.class,
        () -> new OperationsKafkaProperties("events", Duration.ofNanos(1)));
    assertThrows(
        IllegalArgumentException.class,
        () -> new OperationsKafkaProperties("events", Duration.ofMinutes(11)));
  }
}
