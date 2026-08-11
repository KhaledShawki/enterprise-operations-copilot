package io.github.khaledshawki.eoc.platform.connectormanagement.configuration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class ConnectorDeadLetterRecoveryPropertiesTest {

  @Test
  void acceptsABoundedRecoveryPolicy() {
    ConnectorDeadLetterRecoveryProperties properties = properties();

    assertEquals(100, properties.maxPageSize());
    assertEquals(3, properties.maxReplayGeneration());
    assertEquals(5, properties.maxAttempts());
    assertEquals(Duration.ofSeconds(30), properties.claimLease());
  }

  @Test
  void rejectsUnboundedOrNonPositiveSettings() {
    assertThrows(IllegalArgumentException.class, () -> properties(Duration.ZERO, 100, 3, 5, 1));
    assertThrows(
        IllegalArgumentException.class, () -> properties(Duration.ofSeconds(1), 0, 3, 5, 1));
    assertThrows(
        IllegalArgumentException.class, () -> properties(Duration.ofSeconds(1), 100, 0, 5, 1));
    assertThrows(
        IllegalArgumentException.class, () -> properties(Duration.ofSeconds(1), 100, 3, 0, 1));
    assertThrows(
        IllegalArgumentException.class, () -> properties(Duration.ofSeconds(1), 100, 3, 5, 0));
  }

  @Test
  void startupRequiresTheClaimLeaseToCoverTheSequentialKafkaPublicationBudget() {
    ConnectorKafkaProperties kafka =
        new ConnectorKafkaProperties(
            "connector.events", Duration.ofSeconds(10), Duration.ofSeconds(5));
    ConnectorDeadLetterRecoveryProperties unsafe =
        new ConnectorDeadLetterRecoveryProperties(
            true,
            Duration.ofSeconds(1),
            100,
            3,
            5,
            Duration.ofSeconds(30),
            2,
            Duration.ofSeconds(30));

    assertThrows(
        IllegalStateException.class,
        () ->
            ConnectorDeadLetterRecoveryConfiguration.requireClaimLeaseExceedsPublicationBudget(
                kafka, unsafe));
  }

  private static ConnectorDeadLetterRecoveryProperties properties() {
    return properties(Duration.ofSeconds(3), 100, 3, 5, 1);
  }

  private static ConnectorDeadLetterRecoveryProperties properties(
      Duration timeout, int pageSize, int generations, int attempts, int batchSize) {
    return new ConnectorDeadLetterRecoveryProperties(
        true,
        timeout,
        pageSize,
        generations,
        attempts,
        Duration.ofSeconds(30),
        batchSize,
        Duration.ofSeconds(30));
  }
}
