package io.github.khaledshawki.eoc.platform.messaging.kafka;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class PlatformKafkaProducerPropertiesTest {

  @Test
  void acceptsABoundedPositiveMaxBlockTimeout() {
    PlatformKafkaProducerProperties properties =
        new PlatformKafkaProducerProperties(Duration.ofSeconds(5));

    assertEquals(Duration.ofSeconds(5), properties.maxBlockTimeout());
  }

  @Test
  void rejectsAnInvalidMaxBlockTimeout() {
    assertThrows(NullPointerException.class, () -> new PlatformKafkaProducerProperties(null));
    assertThrows(
        IllegalArgumentException.class, () -> new PlatformKafkaProducerProperties(Duration.ZERO));
    assertThrows(
        IllegalArgumentException.class,
        () -> new PlatformKafkaProducerProperties(Duration.ofMillis(-1)));
    assertThrows(
        IllegalArgumentException.class,
        () -> new PlatformKafkaProducerProperties(Duration.ofNanos(1)));
    assertThrows(
        IllegalArgumentException.class,
        () -> new PlatformKafkaProducerProperties(Duration.ofMinutes(11)));
  }
}
