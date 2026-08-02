package io.github.khaledshawki.eoc.platform.connectormanagement.adapter.out.persistence;

import io.github.khaledshawki.eoc.connectormanagement.application.exception.ConcurrentImportRunModificationException;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ConnectorId;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ConnectorTenantId;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ImportCursor;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ImportFailure;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ImportRun;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ImportRunId;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ImportStatistics;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

final class ImportRunPersistenceMapper {

  ImportRunJpaEntity toEntity(ImportRun importRun, Instant now) {
    Objects.requireNonNull(importRun, "Import run cannot be null");
    Objects.requireNonNull(now, "Timestamp cannot be null");
    if (importRun.version() != 0) {
      throw new ConcurrentImportRunModificationException(importRun.id());
    }

    return new ImportRunJpaEntity(
        importRun.id().value(),
        importRun.tenantId().value(),
        importRun.connectorId().value(),
        importRun.importType(),
        importRun.mode(),
        importRun.status(),
        importRun.committedCursor().map(ImportCursor::value).orElse(null),
        importRun.statistics().fetched(),
        importRun.statistics().accepted(),
        importRun.statistics().rejected(),
        importRun.statistics().duplicates(),
        importRun.failure().map(ImportFailure::category).orElse(null),
        importRun.failure().map(ImportFailure::diagnosticCode).orElse(null),
        importRun.attemptCount(),
        importRun.requestedAt(),
        importRun.startedAt().orElse(null),
        importRun.finishedAt().orElse(null),
        importRun.nextRetryAt().orElse(null),
        now,
        now);
  }

  ImportRun toDomain(ImportRunJpaEntity entity) {
    Objects.requireNonNull(entity, "Import run entity cannot be null");
    Optional<ImportFailure> failure =
        entity.getFailureCategory() == null
            ? Optional.empty()
            : Optional.of(new ImportFailure(entity.getFailureCategory(), entity.getFailureCode()));

    return ImportRun.reconstitute(
        ImportRunId.of(entity.getId()),
        ConnectorTenantId.of(entity.getTenantId()),
        ConnectorId.of(entity.getConnectorId()),
        entity.getImportType(),
        entity.getImportMode(),
        entity.getStatus(),
        Optional.ofNullable(entity.getCommittedCursor()).map(ImportCursor::new),
        new ImportStatistics(
            entity.getFetchedCount(),
            entity.getAcceptedCount(),
            entity.getRejectedCount(),
            entity.getDuplicateCount()),
        failure,
        entity.getAttemptCount(),
        entity.getRequestedAt(),
        Optional.ofNullable(entity.getStartedAt()),
        Optional.ofNullable(entity.getFinishedAt()),
        Optional.ofNullable(entity.getNextRetryAt()),
        entity.getVersion());
  }

  ImportRunJpaEntity updateEntity(ImportRun importRun, ImportRunJpaEntity entity, Instant now) {
    Objects.requireNonNull(importRun, "Import run cannot be null");
    Objects.requireNonNull(entity, "Import run entity cannot be null");
    Objects.requireNonNull(now, "Timestamp cannot be null");
    ensureSameImmutableState(importRun, entity);
    if (importRun.version() != entity.getVersion()) {
      throw new ConcurrentImportRunModificationException(importRun.id());
    }

    entity.updateMutableState(
        importRun.status(),
        importRun.committedCursor().map(ImportCursor::value).orElse(null),
        importRun.statistics().fetched(),
        importRun.statistics().accepted(),
        importRun.statistics().rejected(),
        importRun.statistics().duplicates(),
        importRun.failure().map(ImportFailure::category).orElse(null),
        importRun.failure().map(ImportFailure::diagnosticCode).orElse(null),
        importRun.attemptCount(),
        importRun.startedAt().orElse(null),
        importRun.finishedAt().orElse(null),
        importRun.nextRetryAt().orElse(null),
        now);
    return entity;
  }

  private void ensureSameImmutableState(ImportRun importRun, ImportRunJpaEntity entity) {
    if (!entity.getId().equals(importRun.id().value())) {
      throw new IllegalArgumentException("Import run id mismatch");
    }
    if (!entity.getTenantId().equals(importRun.tenantId().value())) {
      throw new IllegalArgumentException("Import run tenant id mismatch");
    }
    if (!entity.getConnectorId().equals(importRun.connectorId().value())) {
      throw new IllegalArgumentException("Import run connector id mismatch");
    }
    if (entity.getImportType() != importRun.importType()) {
      throw new IllegalArgumentException("Import run type mismatch");
    }
    if (entity.getImportMode() != importRun.mode()) {
      throw new IllegalArgumentException("Import run mode mismatch");
    }
    if (!entity.getRequestedAt().equals(importRun.requestedAt())) {
      throw new IllegalArgumentException("Import run request timestamp mismatch");
    }
  }
}
