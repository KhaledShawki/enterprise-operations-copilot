package io.github.khaledshawki.eoc.connectormanagement.application.model.event;

import io.github.khaledshawki.eoc.connectormanagement.domain.model.ImportFailure;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ImportRun;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ImportStatistics;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ImportStatus;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class ImportRunIntegrationEventFactory {

  private ImportRunIntegrationEventFactory() {}

  public static ConnectorIntegrationEvent completed(
      UUID eventId, ImportRun importRun, Instant occurredAt) {
    requireStatus(importRun, ImportStatus.COMPLETED, ImportStatus.PARTIALLY_COMPLETED);
    ImportStatistics statistics = importRun.statistics();
    return event(
        eventId,
        ConnectorIntegrationEventType.IMPORT_RUN_COMPLETED,
        importRun,
        occurredAt,
        new ImportRunCompletedPayload(
            importRun.connectorId().value(),
            importRun.importType().name(),
            importRun.mode().name(),
            importRun.status().name(),
            statistics.fetched(),
            statistics.accepted(),
            statistics.rejected(),
            statistics.duplicates(),
            importRun.attemptCount()));
  }

  public static ConnectorIntegrationEvent failed(
      UUID eventId, ImportRun importRun, Instant occurredAt) {
    requireStatus(importRun, ImportStatus.FAILED);
    return event(
        eventId,
        ConnectorIntegrationEventType.IMPORT_RUN_FAILED,
        importRun,
        occurredAt,
        new ImportRunFailedPayload(
            importRun.connectorId().value(),
            importRun.importType().name(),
            importRun.mode().name(),
            failure(importRun),
            importRun.attemptCount()));
  }

  public static ConnectorIntegrationEvent retryScheduled(
      UUID eventId, ImportRun importRun, Instant occurredAt) {
    requireStatus(importRun, ImportStatus.RETRY_SCHEDULED);
    return event(
        eventId,
        ConnectorIntegrationEventType.IMPORT_RUN_RETRY_SCHEDULED,
        importRun,
        occurredAt,
        new ImportRunRetryScheduledPayload(
            importRun.connectorId().value(),
            importRun.importType().name(),
            importRun.mode().name(),
            failure(importRun),
            importRun.attemptCount(),
            importRun.nextRetryAt().orElseThrow()));
  }

  private static ConnectorIntegrationEvent event(
      UUID eventId,
      ConnectorIntegrationEventType eventType,
      ImportRun importRun,
      Instant occurredAt,
      ConnectorIntegrationEventPayload payload) {
    Objects.requireNonNull(importRun, "Import run cannot be null");
    return new ConnectorIntegrationEvent(
        eventId,
        eventType,
        importRun.tenantId().value(),
        eventType.aggregateType(),
        importRun.id().value(),
        occurredAt,
        payload);
  }

  private static ImportFailurePayload failure(ImportRun importRun) {
    ImportFailure failure = importRun.failure().orElseThrow();
    return new ImportFailurePayload(failure.category().name(), failure.diagnosticCode());
  }

  private static void requireStatus(ImportRun importRun, ImportStatus... statuses) {
    Objects.requireNonNull(importRun, "Import run cannot be null");
    for (ImportStatus status : statuses) {
      if (importRun.status() == status) {
        return;
      }
    }
    throw new IllegalArgumentException(
        "Import run status does not match the integration event contract");
  }
}
