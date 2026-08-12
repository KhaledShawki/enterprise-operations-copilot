package io.github.khaledshawki.eoc.platform.analytics.adapter.in.messaging.kafka;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.khaledshawki.eoc.platform.analytics.configuration.AnalyticsKafkaConsumerProperties;
import io.github.khaledshawki.eoc.platform.messaging.kafka.PlatformKafkaProducerProperties;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class KafkaAnalyticsConsumerConfigurationTest {

  @Test
  void acceptsRecoveryBudgetShorterThanMaxPollInterval() {
    assertDoesNotThrow(
        () ->
            KafkaAnalyticsConsumerConfiguration.requireRecoveryBudgetWithinPollInterval(
                new PlatformKafkaProducerProperties(Duration.ofSeconds(5)),
                properties(Duration.ofSeconds(1), 4, Duration.ofSeconds(10)),
                Duration.ofMinutes(5)));
  }

  @Test
  void rejectsRecoveryBudgetThatCanExhaustMaxPollInterval() {
    assertThrows(
        IllegalStateException.class,
        () ->
            KafkaAnalyticsConsumerConfiguration.requireRecoveryBudgetWithinPollInterval(
                new PlatformKafkaProducerProperties(Duration.ofSeconds(5)),
                properties(Duration.ofSeconds(10), 4, Duration.ofSeconds(10)),
                Duration.ofSeconds(40)));
  }

  private static AnalyticsKafkaConsumerProperties properties(
      Duration backoff, int maxAttempts, Duration dltTimeout) {
    return new AnalyticsKafkaConsumerProperties(
        true,
        "analytics-test-group",
        "analytics-test.dlt",
        maxAttempts,
        backoff,
        dltTimeout,
        1024,
        1);
  }
}
