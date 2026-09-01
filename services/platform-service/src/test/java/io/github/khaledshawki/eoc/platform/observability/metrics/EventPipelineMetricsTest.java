package io.github.khaledshawki.eoc.platform.observability.metrics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class EventPipelineMetricsTest {

  @Test
  void recordsBoundedPipelineMetricsWithoutBusinessIdentifiers() {
    SimpleMeterRegistry registry = new SimpleMeterRegistry();
    EventPipelineMetrics metrics = new EventPipelineMetrics(registry);

    Timer.Sample outboxSample = metrics.startTimer();
    metrics.recordOutboxPublication(EventPipeline.OPERATIONS, 3, 2, outboxSample);
    metrics.updateOutboxBacklog(EventPipeline.OPERATIONS, 7, 11);
    metrics.recordInboxReceived(EventPipeline.ANALYTICS);
    metrics.recordInboxDuplicate(EventPipeline.ANALYTICS);
    metrics.recordInboxFailure(EventPipeline.ANALYTICS);
    Timer.Sample projectionSample = metrics.startTimer();
    metrics.recordProjectionApplied(projectionSample, Duration.ofSeconds(9));
    metrics.recordProjectionVersionGap();
    metrics.recordProjectionStaleEvent();
    metrics.recordDeadLetter(EventPipeline.ANALYTICS);
    metrics.recordDeadLetterReplay(2, 1);

    assertCounter(registry, "eoc.outbox.publish", EventPipeline.OPERATIONS, 3);
    assertCounter(registry, "eoc.outbox.publish.failure", EventPipeline.OPERATIONS, 2);
    assertGauge(registry, "eoc.outbox.pending", EventPipeline.OPERATIONS, 7);
    assertGauge(registry, "eoc.outbox.oldest.seconds", EventPipeline.OPERATIONS, 11);
    assertCounter(registry, "eoc.inbox.received", EventPipeline.ANALYTICS, 1);
    assertCounter(registry, "eoc.inbox.duplicate", EventPipeline.ANALYTICS, 1);
    assertCounter(registry, "eoc.inbox.failure", EventPipeline.ANALYTICS, 1);
    assertCounter(registry, "eoc.projection.apply", EventPipeline.ANALYTICS, 1);
    assertCounter(registry, "eoc.projection.version.gap", EventPipeline.ANALYTICS, 1);
    assertCounter(registry, "eoc.projection.stale.event", EventPipeline.ANALYTICS, 1);
    assertCounter(registry, "eoc.dlt", EventPipeline.ANALYTICS, 1);
    assertCounter(registry, "eoc.dlt.replay", EventPipeline.CONNECTOR, 2);
    assertCounter(registry, "eoc.dlt.replay.failure", EventPipeline.CONNECTOR, 1);

    Timer outboxTimer =
        registry
            .find("eoc.outbox.publish.duration")
            .tag(EventPipelineMetrics.PIPELINE_TAG, "operations")
            .timer();
    Timer projectionTimer =
        registry
            .find("eoc.projection.apply.duration")
            .tag(EventPipelineMetrics.PIPELINE_TAG, "analytics")
            .timer();
    Timer freshnessTimer =
        registry
            .find("eoc.projection.freshness")
            .tag(EventPipelineMetrics.PIPELINE_TAG, "analytics")
            .timer();
    assertNotNull(outboxTimer);
    assertNotNull(projectionTimer);
    assertNotNull(freshnessTimer);
    assertEquals(1, outboxTimer.count());
    assertEquals(1, projectionTimer.count());
    assertEquals(1, freshnessTimer.count());
    assertEquals(9.0, freshnessTimer.totalTime(java.util.concurrent.TimeUnit.SECONDS));
  }

  @Test
  void doesNotRecordOutboxDurationForEmptySchedulerPolls() {
    SimpleMeterRegistry registry = new SimpleMeterRegistry();
    EventPipelineMetrics metrics = new EventPipelineMetrics(registry);

    metrics.recordOutboxPublication(EventPipeline.CONNECTOR, 0, 0, metrics.startTimer());

    Timer outboxTimer =
        registry
            .find("eoc.outbox.publish.duration")
            .tag(EventPipelineMetrics.PIPELINE_TAG, "connector")
            .timer();
    assertNotNull(outboxTimer);
    assertEquals(0, outboxTimer.count());
  }

  private static void assertCounter(
      SimpleMeterRegistry registry, String name, EventPipeline pipeline, double expected) {
    Counter counter =
        registry.find(name).tag(EventPipelineMetrics.PIPELINE_TAG, pipeline.tagValue()).counter();
    assertNotNull(counter);
    assertEquals(expected, counter.count());
  }

  private static void assertGauge(
      SimpleMeterRegistry registry, String name, EventPipeline pipeline, double expected) {
    Gauge gauge =
        registry.find(name).tag(EventPipelineMetrics.PIPELINE_TAG, pipeline.tagValue()).gauge();
    assertNotNull(gauge);
    assertEquals(expected, gauge.value());
  }
}
