package io.github.khaledshawki.eoc.platform.operations.configuration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class OperationsOutboxPropertiesTest {

  @Test
  void acceptsABoundedRuntimePolicy() {
    OperationsOutboxProperties properties = properties(1, Duration.ofSeconds(30), 5, 0, 1000);

    assertEquals(1, properties.batchSize());
    assertEquals(Duration.ofSeconds(30), properties.claimLease());
    assertEquals(5, properties.maxAttempts());
  }

  @Test
  void rejectsInvalidRuntimeSettings() {
    assertThrows(
        IllegalArgumentException.class, () -> properties(0, Duration.ofSeconds(30), 5, 0, 1000));
    assertThrows(
        IllegalArgumentException.class, () -> properties(101, Duration.ofSeconds(30), 5, 0, 1000));
    assertThrows(IllegalArgumentException.class, () -> properties(1, Duration.ZERO, 5, 0, 1000));
    assertThrows(
        IllegalArgumentException.class, () -> properties(1, Duration.ofSeconds(30), 0, 0, 1000));
    assertThrows(
        IllegalArgumentException.class, () -> properties(1, Duration.ofSeconds(30), 5, -1, 1000));
    assertThrows(
        IllegalArgumentException.class, () -> properties(1, Duration.ofSeconds(30), 5, 0, 0));
  }

  private static OperationsOutboxProperties properties(
      int batchSize, Duration claimLease, int maxAttempts, long initialDelayMs, long fixedDelayMs) {
    return new OperationsOutboxProperties(
        true,
        batchSize,
        claimLease,
        maxAttempts,
        Duration.ofMinutes(1),
        initialDelayMs,
        fixedDelayMs);
  }
}
