package io.github.khaledshawki.eoc.connectormanagement.application.port.in;

import io.github.khaledshawki.eoc.connectormanagement.domain.model.ConnectorId;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ConnectorTenantId;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ImportCursor;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ImportFailure;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ImportMode;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ImportRun;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ImportRunId;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ImportStatistics;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ImportStatus;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ImportType;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

public record ImportRunResult(
    ImportRunId importRunId,
    ConnectorTenantId tenantId,
    ConnectorId connectorId,
    ImportType importType,
    ImportMode mode,
    ImportStatus status,
    Optional<ImportCursor> committedCursor,
    ImportStatistics statistics,
    Optional<ImportFailure> failure,
    int attemptCount,
    Instant requestedAt,
    Optional<Instant> startedAt,
    Optional<Instant> finishedAt,
    Optional<Instant> nextRetryAt,
    long version) {

  public ImportRunResult {
    Objects.requireNonNull(importRunId, "Import run id cannot be null");
    Objects.requireNonNull(tenantId, "Import run tenant id cannot be null");
    Objects.requireNonNull(connectorId, "Import run connector id cannot be null");
    Objects.requireNonNull(importType, "Import run type cannot be null");
    Objects.requireNonNull(mode, "Import run mode cannot be null");
    Objects.requireNonNull(status, "Import run status cannot be null");
    Objects.requireNonNull(committedCursor, "Import run cursor cannot be null");
    Objects.requireNonNull(statistics, "Import run statistics cannot be null");
    Objects.requireNonNull(failure, "Import run failure cannot be null");
    Objects.requireNonNull(requestedAt, "Import run request timestamp cannot be null");
    Objects.requireNonNull(startedAt, "Import run start timestamp cannot be null");
    Objects.requireNonNull(finishedAt, "Import run finish timestamp cannot be null");
    Objects.requireNonNull(nextRetryAt, "Import run next retry timestamp cannot be null");
  }

  public static ImportRunResult from(ImportRun importRun) {
    Objects.requireNonNull(importRun, "Import run cannot be null");
    return new ImportRunResult(
        importRun.id(),
        importRun.tenantId(),
        importRun.connectorId(),
        importRun.importType(),
        importRun.mode(),
        importRun.status(),
        importRun.committedCursor(),
        importRun.statistics(),
        importRun.failure(),
        importRun.attemptCount(),
        importRun.requestedAt(),
        importRun.startedAt(),
        importRun.finishedAt(),
        importRun.nextRetryAt(),
        importRun.version());
  }
}
