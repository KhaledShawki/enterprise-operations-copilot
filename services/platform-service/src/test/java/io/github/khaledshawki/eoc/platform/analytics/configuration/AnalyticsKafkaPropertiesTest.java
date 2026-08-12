package io.github.khaledshawki.eoc.platform.analytics.configuration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class AnalyticsKafkaPropertiesTest {

  @Test
  void acceptsValidSourceAndConsumerConfiguration() {
    AnalyticsKafkaProperties source =
        new AnalyticsKafkaProperties("eoc.operations.integration-events");
    AnalyticsKafkaConsumerProperties consumer =
        new AnalyticsKafkaConsumerProperties(
            true,
            "eoc-platform-analytics-v1",
            "eoc.analytics.operations-events.dlt",
            4,
            Duration.ofSeconds(1),
            Duration.ofSeconds(10),
            1_048_576,
            3);

    assertEquals("eoc.operations.integration-events", source.sourceTopic());
    assertEquals(4, consumer.maxAttempts());
    assertEquals(3, consumer.concurrency());
  }

  @Test
  void rejectsInvalidTopicsAndConsumerBudgets() {
    assertThrows(IllegalArgumentException.class, () -> new AnalyticsKafkaProperties("bad topic"));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new AnalyticsKafkaConsumerProperties(
                true,
                "bad group!",
                "eoc.analytics.dlt",
                4,
                Duration.ofSeconds(1),
                Duration.ofSeconds(10),
                1024,
                1));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new AnalyticsKafkaConsumerProperties(
                true,
                "group",
                "eoc.analytics.dlt",
                0,
                Duration.ZERO,
                Duration.ofSeconds(10),
                1024,
                1));
  }
}
