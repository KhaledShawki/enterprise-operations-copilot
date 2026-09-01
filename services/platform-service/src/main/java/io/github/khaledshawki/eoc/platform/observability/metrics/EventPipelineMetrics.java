package io.github.khaledshawki.eoc.platform.observability.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

public final class EventPipelineMetrics {

  static final String PIPELINE_TAG = "pipeline";

  private final MeterRegistry registry;
  private final Map<EventPipeline, Counter> outboxPublished = new EnumMap<>(EventPipeline.class);
  private final Map<EventPipeline, Counter> outboxFailures = new EnumMap<>(EventPipeline.class);
  private final Map<EventPipeline, Timer> outboxPublishDuration =
      new EnumMap<>(EventPipeline.class);
  private final Map<EventPipeline, AtomicLong> outboxPending = new EnumMap<>(EventPipeline.class);
  private final Map<EventPipeline, AtomicLong> outboxOldestSeconds =
      new EnumMap<>(EventPipeline.class);
  private final Map<EventPipeline, Counter> inboxReceived = new EnumMap<>(EventPipeline.class);
  private final Map<EventPipeline, Counter> inboxDuplicates = new EnumMap<>(EventPipeline.class);
  private final Map<EventPipeline, Counter> inboxFailures = new EnumMap<>(EventPipeline.class);
  private final Map<EventPipeline, Counter> deadLetters = new EnumMap<>(EventPipeline.class);
  private final Counter projectionApplied;
  private final Timer projectionApplyDuration;
  private final Timer projectionFreshness;
  private final Counter projectionVersionGaps;
  private final Counter projectionStaleEvents;
  private final Counter deadLetterReplayed;
  private final Counter deadLetterReplayFailures;

  public EventPipelineMetrics(MeterRegistry registry) {
    this.registry = Objects.requireNonNull(registry, "Meter registry cannot be null");
    for (EventPipeline pipeline :
        new EventPipeline[] {EventPipeline.CONNECTOR, EventPipeline.OPERATIONS}) {
      String tagValue = pipeline.tagValue();
      outboxPublished.put(
          pipeline,
          Counter.builder("eoc.outbox.publish")
              .description("Successfully published outbox events")
              .tag(PIPELINE_TAG, tagValue)
              .register(registry));
      outboxFailures.put(
          pipeline,
          Counter.builder("eoc.outbox.publish.failure")
              .description("Outbox publication attempts that require retry or failed terminally")
              .tag(PIPELINE_TAG, tagValue)
              .register(registry));
      outboxPublishDuration.put(
          pipeline,
          Timer.builder("eoc.outbox.publish.duration")
              .description("Outbox batch publication duration")
              .publishPercentileHistogram()
              .tag(PIPELINE_TAG, tagValue)
              .register(registry));
      AtomicLong pending = new AtomicLong();
      AtomicLong oldestSeconds = new AtomicLong();
      outboxPending.put(pipeline, pending);
      outboxOldestSeconds.put(pipeline, oldestSeconds);
      Gauge.builder("eoc.outbox.pending", pending, AtomicLong::doubleValue)
          .description("Outbox events awaiting successful publication")
          .tag(PIPELINE_TAG, tagValue)
          .register(registry);
      Gauge.builder("eoc.outbox.oldest.seconds", oldestSeconds, AtomicLong::doubleValue)
          .description("Age in seconds of the oldest outstanding outbox event")
          .tag(PIPELINE_TAG, tagValue)
          .register(registry);
    }

    for (EventPipeline pipeline :
        new EventPipeline[] {EventPipeline.CONNECTOR, EventPipeline.ANALYTICS}) {
      String tagValue = pipeline.tagValue();
      inboxReceived.put(
          pipeline,
          Counter.builder("eoc.inbox.received")
              .description("Inbox deliveries received from the transport")
              .tag(PIPELINE_TAG, tagValue)
              .register(registry));
      inboxDuplicates.put(
          pipeline,
          Counter.builder("eoc.inbox.duplicate")
              .description("Inbox deliveries recognized as duplicates")
              .tag(PIPELINE_TAG, tagValue)
              .register(registry));
      inboxFailures.put(
          pipeline,
          Counter.builder("eoc.inbox.failure")
              .description("Inbox delivery attempts that failed processing")
              .tag(PIPELINE_TAG, tagValue)
              .register(registry));
      deadLetters.put(
          pipeline,
          Counter.builder("eoc.dlt")
              .description("Records successfully published to a dead-letter topic")
              .tag(PIPELINE_TAG, tagValue)
              .register(registry));
    }

    projectionApplied =
        Counter.builder("eoc.projection.apply")
            .description("Analytics projection applications")
            .tag(PIPELINE_TAG, EventPipeline.ANALYTICS.tagValue())
            .register(registry);
    projectionApplyDuration =
        Timer.builder("eoc.projection.apply.duration")
            .description("Analytics projection application duration")
            .publishPercentileHistogram()
            .tag(PIPELINE_TAG, EventPipeline.ANALYTICS.tagValue())
            .register(registry);
    projectionFreshness =
        Timer.builder("eoc.projection.freshness")
            .description("Age of an Operations event when its Analytics projection is applied")
            .publishPercentileHistogram()
            .maximumExpectedValue(Duration.ofMinutes(30))
            .tag(PIPELINE_TAG, EventPipeline.ANALYTICS.tagValue())
            .register(registry);
    projectionVersionGaps =
        Counter.builder("eoc.projection.version.gap")
            .description("Analytics projection version gaps")
            .tag(PIPELINE_TAG, EventPipeline.ANALYTICS.tagValue())
            .register(registry);
    projectionStaleEvents =
        Counter.builder("eoc.projection.stale.event")
            .description("Analytics events older than the current projection version")
            .tag(PIPELINE_TAG, EventPipeline.ANALYTICS.tagValue())
            .register(registry);
    deadLetterReplayed =
        Counter.builder("eoc.dlt.replay")
            .description("Connector dead-letter records successfully replayed")
            .tag(PIPELINE_TAG, EventPipeline.CONNECTOR.tagValue())
            .register(registry);
    deadLetterReplayFailures =
        Counter.builder("eoc.dlt.replay.failure")
            .description("Connector dead-letter replay attempts that require retry or failed")
            .tag(PIPELINE_TAG, EventPipeline.CONNECTOR.tagValue())
            .register(registry);
  }

  public Timer.Sample startTimer() {
    return Timer.start(registry);
  }

  public void recordOutboxPublication(
      EventPipeline pipeline, int published, int failures, Timer.Sample sample) {
    requireNonNegative(published, "Published outbox count");
    requireNonNegative(failures, "Failed outbox count");
    EventPipeline requiredPipeline = requirePipeline(pipeline);
    outboxPublished.get(requiredPipeline).increment(published);
    outboxFailures.get(requiredPipeline).increment(failures);
    Timer.Sample requiredSample =
        Objects.requireNonNull(sample, "Outbox publication timer sample cannot be null");
    if (published > 0 || failures > 0) {
      requiredSample.stop(outboxPublishDuration.get(requiredPipeline));
    }
  }

  public void updateOutboxBacklog(EventPipeline pipeline, long pending, long oldestSeconds) {
    if (pending < 0 || oldestSeconds < 0) {
      throw new IllegalArgumentException("Outbox backlog metrics cannot be negative");
    }
    EventPipeline requiredPipeline = requirePipeline(pipeline);
    outboxPending.get(requiredPipeline).set(pending);
    outboxOldestSeconds.get(requiredPipeline).set(oldestSeconds);
  }

  public void recordInboxReceived(EventPipeline pipeline) {
    inboxReceived.get(requirePipeline(pipeline)).increment();
  }

  public void recordInboxDuplicate(EventPipeline pipeline) {
    inboxDuplicates.get(requirePipeline(pipeline)).increment();
  }

  public void recordInboxFailure(EventPipeline pipeline) {
    inboxFailures.get(requirePipeline(pipeline)).increment();
  }

  public void recordProjectionApplied(Timer.Sample sample, Duration freshness) {
    projectionApplied.increment();
    Objects.requireNonNull(sample, "Projection timer sample cannot be null")
        .stop(projectionApplyDuration);
    Duration requiredFreshness =
        Objects.requireNonNull(freshness, "Projection freshness cannot be null");
    if (requiredFreshness.isNegative()) {
      throw new IllegalArgumentException("Projection freshness cannot be negative");
    }
    projectionFreshness.record(requiredFreshness);
  }

  public void recordProjectionVersionGap() {
    projectionVersionGaps.increment();
  }

  public void recordProjectionStaleEvent() {
    projectionStaleEvents.increment();
  }

  public void recordDeadLetter(EventPipeline pipeline) {
    deadLetters.get(requirePipeline(pipeline)).increment();
  }

  public void recordDeadLetterReplay(int replayed, int failures) {
    requireNonNegative(replayed, "Replayed dead-letter count");
    requireNonNegative(failures, "Failed dead-letter replay count");
    deadLetterReplayed.increment(replayed);
    deadLetterReplayFailures.increment(failures);
  }

  private static EventPipeline requirePipeline(EventPipeline pipeline) {
    return Objects.requireNonNull(pipeline, "Event pipeline cannot be null");
  }

  private static void requireNonNegative(int value, String description) {
    if (value < 0) {
      throw new IllegalArgumentException(description + " cannot be negative");
    }
  }
}
