package io.github.khaledshawki.eoc.platform.observability.metrics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

class CopilotMetricsTest {

  @Test
  void recordsBoundedSuccessAndFailureOutcomes() {
    SimpleMeterRegistry registry = new SimpleMeterRegistry();
    CopilotMetrics metrics = new CopilotMetrics(registry);

    metrics.recordSuccess(metrics.startTimer());
    metrics.recordFailure(metrics.startTimer());

    assertOutcome(registry, "success");
    assertOutcome(registry, "failure");
  }

  private static void assertOutcome(SimpleMeterRegistry registry, String outcome) {
    Counter counter =
        registry.find("eoc.copilot.request").tag(CopilotMetrics.OUTCOME_TAG, outcome).counter();
    Timer timer =
        registry.find("eoc.copilot.duration").tag(CopilotMetrics.OUTCOME_TAG, outcome).timer();
    assertNotNull(counter);
    assertNotNull(timer);
    assertEquals(1.0, counter.count());
    assertEquals(1, timer.count());
  }
}
