package io.github.khaledshawki.eoc.connectormanagement.domain.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class SyncPolicyTest {

  @Test
  void shouldCreateManualPolicyWithoutInterval() {
    SyncPolicy policy = SyncPolicy.manual();

    assertEquals(SyncPolicy.Mode.MANUAL, policy.mode());
    assertEquals(Duration.ZERO, policy.interval());
  }

  @Test
  void shouldCreateScheduledPolicyWithPositiveInterval() {
    Duration interval = Duration.ofMinutes(15);

    SyncPolicy policy = SyncPolicy.scheduled(interval);

    assertEquals(SyncPolicy.Mode.SCHEDULED, policy.mode());
    assertEquals(interval, policy.interval());
  }

  @Test
  void shouldRejectInvalidModeAndIntervalCombinations() {
    assertThrows(NullPointerException.class, () -> new SyncPolicy(null, Duration.ZERO));
    assertThrows(NullPointerException.class, () -> new SyncPolicy(SyncPolicy.Mode.MANUAL, null));
    assertThrows(
        IllegalArgumentException.class,
        () -> new SyncPolicy(SyncPolicy.Mode.MANUAL, Duration.ofMinutes(1)));
    assertThrows(IllegalArgumentException.class, () -> SyncPolicy.scheduled(Duration.ZERO));
    assertThrows(
        IllegalArgumentException.class, () -> SyncPolicy.scheduled(Duration.ofSeconds(-1)));
  }
}
