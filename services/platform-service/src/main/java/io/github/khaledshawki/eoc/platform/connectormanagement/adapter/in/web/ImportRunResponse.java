package io.github.khaledshawki.eoc.platform.connectormanagement.adapter.in.web;

import io.github.khaledshawki.eoc.connectormanagement.application.port.in.ImportRunResult;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record ImportRunResponse(
    UUID id,
    UUID tenantId,
    UUID connectorId,
    String importType,
    String mode,
    String status,
    String committedCursor,
    StatisticsResponse statistics,
    FailureResponse failure,
    int attemptCount,
    Instant requestedAt,
    Instant startedAt,
    Instant finishedAt,
    Instant nextRetryAt,
    long version) {

  public ImportRunResponse {
    Objects.requireNonNull(id, "Import run response id cannot be null");
    Objects.requireNonNull(tenantId, "Import run response tenant id cannot be null");
    Objects.requireNonNull(connectorId, "Import run response connector id cannot be null");
    Objects.requireNonNull(importType, "Import run response type cannot be null");
    Objects.requireNonNull(mode, "Import run response mode cannot be null");
    Objects.requireNonNull(status, "Import run response status cannot be null");
    Objects.requireNonNull(statistics, "Import run response statistics cannot be null");
    Objects.requireNonNull(requestedAt, "Import run response request time cannot be null");
  }

  static ImportRunResponse from(ImportRunResult result) {
    Objects.requireNonNull(result, "Import run result cannot be null");
    return new ImportRunResponse(
        result.importRunId().value(),
        result.tenantId().value(),
        result.connectorId().value(),
        result.importType().name(),
        result.mode().name(),
        result.status().name(),
        result.committedCursor().map(cursor -> cursor.value()).orElse(null),
        new StatisticsResponse(
            result.statistics().fetched(),
            result.statistics().accepted(),
            result.statistics().rejected(),
            result.statistics().duplicates()),
        result
            .failure()
            .map(
                failure ->
                    new FailureResponse(
                        failure.category().name(), failure.diagnosticCode(), failure.retryable()))
            .orElse(null),
        result.attemptCount(),
        result.requestedAt(),
        result.startedAt().orElse(null),
        result.finishedAt().orElse(null),
        result.nextRetryAt().orElse(null),
        result.version());
  }

  public record StatisticsResponse(long fetched, long accepted, long rejected, long duplicates) {}

  public record FailureResponse(String category, String diagnosticCode, boolean retryable) {}
}
