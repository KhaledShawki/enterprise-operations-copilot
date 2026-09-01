package io.github.khaledshawki.eoc.platform.observability.metrics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import io.github.khaledshawki.eoc.connectormanagement.domain.model.ImportStatus;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ImportType;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

class ConnectorImportMetricsTest {

  @Test
  void recordsBoundedImportOutcomeAndRetryMetrics() {
    SimpleMeterRegistry registry = new SimpleMeterRegistry();
    ConnectorImportMetrics metrics = new ConnectorImportMetrics(registry);

    metrics.recordExecution(
        ImportType.INVOICES, ImportStatus.RETRY_SCHEDULED, metrics.startTimer());

    Counter execution =
        registry
            .find("eoc.connector.import.execution")
            .tag(ConnectorImportMetrics.IMPORT_TYPE_TAG, "invoices")
            .tag(ConnectorImportMetrics.OUTCOME_TAG, "retry_scheduled")
            .counter();
    Counter retries =
        registry
            .find("eoc.connector.import.retry.scheduled")
            .tag(ConnectorImportMetrics.IMPORT_TYPE_TAG, "invoices")
            .counter();
    Timer duration =
        registry
            .find("eoc.connector.import.duration")
            .tag(ConnectorImportMetrics.IMPORT_TYPE_TAG, "invoices")
            .timer();

    assertNotNull(execution);
    assertNotNull(retries);
    assertNotNull(duration);
    assertEquals(1.0, execution.count());
    assertEquals(1.0, retries.count());
    assertEquals(1, duration.count());
  }

  @Test
  void preservesPartialCompletionAsADistinctBoundedOutcome() {
    SimpleMeterRegistry registry = new SimpleMeterRegistry();
    ConnectorImportMetrics metrics = new ConnectorImportMetrics(registry);

    metrics.recordExecution(
        ImportType.INVOICES, ImportStatus.PARTIALLY_COMPLETED, metrics.startTimer());

    Counter partial =
        registry
            .find("eoc.connector.import.execution")
            .tag(ConnectorImportMetrics.IMPORT_TYPE_TAG, "invoices")
            .tag(ConnectorImportMetrics.OUTCOME_TAG, "partial")
            .counter();
    assertNotNull(partial);
    assertEquals(1.0, partial.count());
  }

  @Test
  void recordsUnexpectedFailureWithoutBusinessIdentifierTag() {
    SimpleMeterRegistry registry = new SimpleMeterRegistry();
    ConnectorImportMetrics metrics = new ConnectorImportMetrics(registry);

    metrics.recordUnexpectedFailure(metrics.startTimer());

    Counter execution =
        registry
            .find("eoc.connector.import.execution")
            .tag(ConnectorImportMetrics.IMPORT_TYPE_TAG, ConnectorImportMetrics.UNKNOWN_IMPORT_TYPE)
            .tag(ConnectorImportMetrics.OUTCOME_TAG, "failure")
            .counter();
    assertNotNull(execution);
    assertEquals(1.0, execution.count());
  }
}
