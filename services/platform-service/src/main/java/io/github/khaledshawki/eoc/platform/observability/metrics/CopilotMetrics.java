package io.github.khaledshawki.eoc.platform.observability.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

public final class CopilotMetrics {

  static final String OUTCOME_TAG = "outcome";

  private final MeterRegistry registry;
  private final Map<Outcome, Counter> requests = new EnumMap<>(Outcome.class);
  private final Map<Outcome, Timer> durations = new EnumMap<>(Outcome.class);

  public CopilotMetrics(MeterRegistry registry) {
    this.registry = Objects.requireNonNull(registry, "Meter registry cannot be null");
    for (Outcome outcome : Outcome.values()) {
      requests.put(
          outcome,
          Counter.builder("eoc.copilot.request")
              .description("Copilot requests by outcome")
              .tag(OUTCOME_TAG, outcome.tagValue)
              .register(registry));
      durations.put(
          outcome,
          Timer.builder("eoc.copilot.duration")
              .description("Copilot request duration by outcome")
              .publishPercentileHistogram()
              .maximumExpectedValue(Duration.ofMinutes(30))
              .tag(OUTCOME_TAG, outcome.tagValue)
              .register(registry));
    }
  }

  public Timer.Sample startTimer() {
    return Timer.start(registry);
  }

  public void recordSuccess(Timer.Sample sample) {
    record(Outcome.SUCCESS, sample);
  }

  public void recordFailure(Timer.Sample sample) {
    record(Outcome.FAILURE, sample);
  }

  private void record(Outcome outcome, Timer.Sample sample) {
    requests.get(outcome).increment();
    Objects.requireNonNull(sample, "Copilot timer sample cannot be null")
        .stop(durations.get(outcome));
  }

  private enum Outcome {
    SUCCESS("success"),
    FAILURE("failure");

    private final String tagValue;

    Outcome(String tagValue) {
      this.tagValue = tagValue;
    }
  }
}
