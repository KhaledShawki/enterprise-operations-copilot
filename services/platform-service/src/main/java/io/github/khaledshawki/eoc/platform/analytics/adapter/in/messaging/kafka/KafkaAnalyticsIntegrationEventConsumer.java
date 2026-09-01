package io.github.khaledshawki.eoc.platform.analytics.adapter.in.messaging.kafka;

import io.github.khaledshawki.eoc.analytics.application.exception.AnalyticsEventConsumptionException;
import io.github.khaledshawki.eoc.analytics.application.exception.AnalyticsProjectionConcurrentModificationException;
import io.github.khaledshawki.eoc.analytics.application.exception.AnalyticsProjectionStateCorruptedException;
import io.github.khaledshawki.eoc.analytics.application.exception.AnalyticsProjectionVersionConflictException;
import io.github.khaledshawki.eoc.analytics.application.exception.AnalyticsProjectionVersionGapException;
import io.github.khaledshawki.eoc.analytics.application.exception.AnalyticsProjectionVersionRegressionException;
import io.github.khaledshawki.eoc.analytics.application.model.event.AnalyticsEventConsumptionResult;
import io.github.khaledshawki.eoc.analytics.application.model.event.AnalyticsEventConsumptionStatus;
import io.github.khaledshawki.eoc.analytics.application.model.event.AnalyticsIntegrationEvent;
import io.github.khaledshawki.eoc.analytics.application.port.in.ConsumeAnalyticsIntegrationEventUseCase;
import io.github.khaledshawki.eoc.platform.analytics.adapter.messaging.kafka.AnalyticsKafkaRecordKey;
import io.github.khaledshawki.eoc.platform.observability.metrics.EventPipeline;
import io.github.khaledshawki.eoc.platform.observability.metrics.EventPipelineMetrics;
import io.micrometer.core.instrument.Timer;
import java.time.Clock;
import java.time.Duration;
import java.util.Objects;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;

final class KafkaAnalyticsIntegrationEventConsumer {

  static final String KEY_MISMATCH = "kafka-analytics-event-key-mismatch";
  static final String CONSUMPTION_FAILED = "kafka-analytics-event-consumption-failed";
  static final String CONCURRENT_MODIFICATION = "analytics-projection-concurrent-modification";
  static final String VERSION_GAP = "analytics-projection-version-gap";
  static final String VERSION_CONFLICT = "analytics-projection-version-conflict";
  static final String VERSION_REGRESSION = "analytics-projection-version-regression";
  static final String STATE_CORRUPTED = "analytics-projection-state-corrupted";
  static final String PROJECTION_INVALID = "analytics-projection-invalid";

  private final KafkaAnalyticsIntegrationEventDecoder decoder;
  private final ConsumeAnalyticsIntegrationEventUseCase useCase;
  private final EventPipelineMetrics metrics;
  private final Clock clock;

  KafkaAnalyticsIntegrationEventConsumer(
      KafkaAnalyticsIntegrationEventDecoder decoder,
      ConsumeAnalyticsIntegrationEventUseCase useCase,
      EventPipelineMetrics metrics,
      Clock clock) {
    this.decoder = Objects.requireNonNull(decoder, "Kafka Analytics event decoder cannot be null");
    this.useCase = Objects.requireNonNull(useCase, "Analytics event use case cannot be null");
    this.metrics = Objects.requireNonNull(metrics, "Event pipeline metrics cannot be null");
    this.clock = Objects.requireNonNull(clock, "Clock cannot be null");
  }

  @KafkaListener(
      id = "analytics-integration-event-consumer",
      clientIdPrefix = "eoc-analytics-inbox",
      topics = "${eoc.analytics-events.kafka.source-topic}",
      groupId = "${eoc.analytics-events.kafka.consumer.group-id}",
      containerFactory = "analyticsKafkaListenerContainerFactory")
  void consume(ConsumerRecord<String, String> record) {
    Objects.requireNonNull(record, "Kafka Analytics consumer record cannot be null");
    metrics.recordInboxReceived(EventPipeline.ANALYTICS);
    try {
      AnalyticsIntegrationEvent event = decoder.decode(record.value());
      if (!AnalyticsKafkaRecordKey.from(event).equals(record.key())) {
        throw new TerminalAnalyticsKafkaConsumptionException(KEY_MISMATCH, null);
      }

      Timer.Sample sample = metrics.startTimer();
      AnalyticsEventConsumptionResult result = useCase.consume(event);
      if (result.status() == AnalyticsEventConsumptionStatus.APPLIED) {
        Duration freshness = Duration.between(event.occurredAt(), clock.instant());
        metrics.recordProjectionApplied(sample, freshness.isNegative() ? Duration.ZERO : freshness);
      } else if (result.status() == AnalyticsEventConsumptionStatus.DUPLICATE) {
        metrics.recordInboxDuplicate(EventPipeline.ANALYTICS);
      }
    } catch (AnalyticsKafkaConsumptionException exception) {
      metrics.recordInboxFailure(EventPipeline.ANALYTICS);
      throw exception;
    } catch (AnalyticsEventConsumptionException exception) {
      metrics.recordInboxFailure(EventPipeline.ANALYTICS);
      if (exception.retryable()) {
        throw new RetryableAnalyticsKafkaConsumptionException(exception.failureCode(), exception);
      }
      throw new TerminalAnalyticsKafkaConsumptionException(exception.failureCode(), exception);
    } catch (AnalyticsProjectionConcurrentModificationException exception) {
      metrics.recordInboxFailure(EventPipeline.ANALYTICS);
      throw new RetryableAnalyticsKafkaConsumptionException(CONCURRENT_MODIFICATION, exception);
    } catch (AnalyticsProjectionVersionGapException exception) {
      metrics.recordInboxFailure(EventPipeline.ANALYTICS);
      metrics.recordProjectionVersionGap();
      throw new RetryableAnalyticsKafkaConsumptionException(VERSION_GAP, exception);
    } catch (AnalyticsProjectionVersionConflictException exception) {
      metrics.recordInboxFailure(EventPipeline.ANALYTICS);
      throw new TerminalAnalyticsKafkaConsumptionException(VERSION_CONFLICT, exception);
    } catch (AnalyticsProjectionVersionRegressionException exception) {
      metrics.recordInboxFailure(EventPipeline.ANALYTICS);
      metrics.recordProjectionStaleEvent();
      throw new TerminalAnalyticsKafkaConsumptionException(VERSION_REGRESSION, exception);
    } catch (AnalyticsProjectionStateCorruptedException exception) {
      metrics.recordInboxFailure(EventPipeline.ANALYTICS);
      throw new TerminalAnalyticsKafkaConsumptionException(STATE_CORRUPTED, exception);
    } catch (IllegalArgumentException | NullPointerException exception) {
      metrics.recordInboxFailure(EventPipeline.ANALYTICS);
      throw new TerminalAnalyticsKafkaConsumptionException(PROJECTION_INVALID, exception);
    } catch (RuntimeException exception) {
      metrics.recordInboxFailure(EventPipeline.ANALYTICS);
      throw new RetryableAnalyticsKafkaConsumptionException(CONSUMPTION_FAILED, exception);
    }
  }
}
