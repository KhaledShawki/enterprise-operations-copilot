package io.github.khaledshawki.eoc.platform.observability.metrics;

import io.github.khaledshawki.eoc.connectormanagement.domain.model.ImportStatus;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ImportType;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public final class ConnectorImportMetrics {

  static final String IMPORT_TYPE_TAG = "import_type";
  static final String OUTCOME_TAG = "outcome";
  static final String UNKNOWN_IMPORT_TYPE = "unknown";

  private final MeterRegistry registry;
  private final Map<ImportType, Timer> durationByType = new EnumMap<>(ImportType.class);
  private final Map<ImportType, Counter> retryScheduledByType = new EnumMap<>(ImportType.class);
  private final Timer unknownFailureDuration;
  private final Counter unknownFailureCounter;

  public ConnectorImportMetrics(MeterRegistry registry) {
    this.registry = Objects.requireNonNull(registry, "Meter registry cannot be null");
    for (ImportType importType : ImportType.values()) {
      String tagValue = importTypeTag(importType);
      durationByType.put(
          importType,
          Timer.builder("eoc.connector.import.duration")
              .description("Connector import execution duration")
              .publishPercentileHistogram()
              .maximumExpectedValue(Duration.ofMinutes(30))
              .tag(IMPORT_TYPE_TAG, tagValue)
              .register(registry));
      retryScheduledByType.put(
          importType,
          Counter.builder("eoc.connector.import.retry.scheduled")
              .description("Connector import executions that scheduled another retry")
              .tag(IMPORT_TYPE_TAG, tagValue)
              .register(registry));
      for (ImportOutcome outcome : ImportOutcome.values()) {
        executionCounter(tagValue, outcome.tagValue());
      }
    }
    unknownFailureDuration =
        Timer.builder("eoc.connector.import.duration")
            .description("Connector import execution duration")
            .publishPercentileHistogram()
            .maximumExpectedValue(Duration.ofMinutes(30))
            .tag(IMPORT_TYPE_TAG, UNKNOWN_IMPORT_TYPE)
            .register(registry);
    unknownFailureCounter = executionCounter(UNKNOWN_IMPORT_TYPE, ImportOutcome.FAILURE.tagValue());
  }

  public Timer.Sample startTimer() {
    return Timer.start(registry);
  }

  public void recordExecution(ImportType importType, ImportStatus status, Timer.Sample sample) {
    ImportType requiredType = Objects.requireNonNull(importType, "Import type cannot be null");
    ImportOutcome outcome =
        ImportOutcome.from(Objects.requireNonNull(status, "Import status cannot be null"));
    executionCounter(importTypeTag(requiredType), outcome.tagValue()).increment();
    if (outcome == ImportOutcome.RETRY_SCHEDULED) {
      retryScheduledByType.get(requiredType).increment();
    }
    Objects.requireNonNull(sample, "Connector import timer sample cannot be null")
        .stop(durationByType.get(requiredType));
  }

  public void recordUnexpectedFailure(Timer.Sample sample) {
    unknownFailureCounter.increment();
    Objects.requireNonNull(sample, "Connector import timer sample cannot be null")
        .stop(unknownFailureDuration);
  }

  private Counter executionCounter(String importType, String outcome) {
    return Counter.builder("eoc.connector.import.execution")
        .description("Connector import execution attempts by bounded outcome")
        .tag(IMPORT_TYPE_TAG, importType)
        .tag(OUTCOME_TAG, outcome)
        .register(registry);
  }

  private static String importTypeTag(ImportType importType) {
    return importType.name().toLowerCase(Locale.ROOT);
  }

  private enum ImportOutcome {
    SUCCESS("success"),
    PARTIAL("partial"),
    RETRY_SCHEDULED("retry_scheduled"),
    FAILURE("failure"),
    CANCELLED("cancelled"),
    IN_PROGRESS("in_progress");

    private final String tagValue;

    ImportOutcome(String tagValue) {
      this.tagValue = tagValue;
    }

    String tagValue() {
      return tagValue;
    }

    static ImportOutcome from(ImportStatus status) {
      return switch (status) {
        case COMPLETED -> SUCCESS;
        case PARTIALLY_COMPLETED -> PARTIAL;
        case RETRY_SCHEDULED -> RETRY_SCHEDULED;
        case FAILED -> FAILURE;
        case CANCELLED -> CANCELLED;
        case REQUESTED, RUNNING, CANCELLING -> IN_PROGRESS;
      };
    }
  }
}
