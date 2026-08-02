package io.github.khaledshawki.eoc.connectormanagement.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

public final class ImportRun {

  private final ImportRunId id;
  private final ConnectorTenantId tenantId;
  private final ConnectorId connectorId;
  private final ImportType importType;
  private final ImportMode mode;
  private ImportStatus status;
  private Optional<ImportCursor> committedCursor;
  private ImportStatistics statistics;
  private Optional<ImportFailure> failure;
  private int attemptCount;
  private final Instant requestedAt;
  private Optional<Instant> startedAt;
  private Optional<Instant> finishedAt;
  private Optional<Instant> nextRetryAt;
  private final long version;

  private ImportRun(
      ImportRunId id,
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
    this.id = Objects.requireNonNull(id, "Import run id cannot be null");
    this.tenantId = Objects.requireNonNull(tenantId, "Import run tenant id cannot be null");
    this.connectorId =
        Objects.requireNonNull(connectorId, "Import run connector id cannot be null");
    this.importType = Objects.requireNonNull(importType, "Import run type cannot be null");
    this.mode = Objects.requireNonNull(mode, "Import run mode cannot be null");
    this.status = Objects.requireNonNull(status, "Import run status cannot be null");
    this.committedCursor =
        Objects.requireNonNull(committedCursor, "Import run cursor cannot be null");
    this.statistics = Objects.requireNonNull(statistics, "Import statistics cannot be null");
    this.failure = Objects.requireNonNull(failure, "Import run failure cannot be null");
    this.attemptCount = attemptCount;
    this.requestedAt = Objects.requireNonNull(requestedAt, "Request timestamp cannot be null");
    this.startedAt = Objects.requireNonNull(startedAt, "Start timestamp cannot be null");
    this.finishedAt = Objects.requireNonNull(finishedAt, "Finish timestamp cannot be null");
    this.nextRetryAt = Objects.requireNonNull(nextRetryAt, "Next retry timestamp cannot be null");
    this.version = version;
    validateState();
  }

  public static ImportRun request(
      ConnectorTenantId tenantId,
      ConnectorId connectorId,
      ImportType importType,
      ImportMode mode,
      Optional<ImportCursor> startingCursor,
      Instant requestedAt) {
    return new ImportRun(
        ImportRunId.generate(),
        tenantId,
        connectorId,
        importType,
        mode,
        ImportStatus.REQUESTED,
        startingCursor,
        ImportStatistics.ZERO,
        Optional.empty(),
        0,
        requestedAt,
        Optional.empty(),
        Optional.empty(),
        Optional.empty(),
        0);
  }

  public static ImportRun reconstitute(
      ImportRunId id,
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
    return new ImportRun(
        id,
        tenantId,
        connectorId,
        importType,
        mode,
        status,
        committedCursor,
        statistics,
        failure,
        attemptCount,
        requestedAt,
        startedAt,
        finishedAt,
        nextRetryAt,
        version);
  }

  public void start(Instant now) {
    Objects.requireNonNull(now, "Start timestamp cannot be null");
    requireStatus(ImportStatus.REQUESTED, ImportStatus.RETRY_SCHEDULED);
    requireNotBeforeRequested(now);
    if (status == ImportStatus.RETRY_SCHEDULED && now.isBefore(nextRetryAt.orElseThrow())) {
      throw new IllegalStateException("Import run cannot start before its scheduled retry time");
    }

    status = ImportStatus.RUNNING;
    startedAt = startedAt.isEmpty() ? Optional.of(now) : startedAt;
    nextRetryAt = Optional.empty();
    failure = Optional.empty();
    attemptCount = Math.addExact(attemptCount, 1);
  }

  public void recordAcceptedPage(
      Optional<ImportCursor> expectedCursor,
      Optional<ImportCursor> candidateCursor,
      ImportStatistics pageStatistics) {
    Objects.requireNonNull(expectedCursor, "Expected import cursor cannot be null");
    Objects.requireNonNull(candidateCursor, "Candidate import cursor cannot be null");
    Objects.requireNonNull(pageStatistics, "Page statistics cannot be null");
    requireStatus(ImportStatus.RUNNING);

    if (!committedCursor.equals(expectedCursor)) {
      throw new IllegalStateException("Import page was based on a stale committed cursor");
    }
    if (mode == ImportMode.INCREMENTAL
        && pageStatistics.fetched() > 0
        && candidateCursor.isEmpty()) {
      throw new IllegalStateException(
          "A non-empty incremental page must provide a candidate cursor");
    }
    if (pageStatistics.fetched() > 0 && candidateCursor.equals(committedCursor)) {
      throw new IllegalStateException("A non-empty import page must advance its candidate cursor");
    }

    statistics = statistics.plus(pageStatistics);
    candidateCursor.ifPresent(cursor -> committedCursor = Optional.of(cursor));
  }

  public void scheduleRetry(ImportFailure failure, Instant nextRetryAt, Instant now) {
    Objects.requireNonNull(failure, "Import failure cannot be null");
    Objects.requireNonNull(nextRetryAt, "Next retry timestamp cannot be null");
    Objects.requireNonNull(now, "Retry scheduling timestamp cannot be null");
    requireStatus(ImportStatus.RUNNING);
    requireNotBeforeStarted(now);
    if (!failure.retryable()) {
      throw new IllegalArgumentException("Only retryable import failures can schedule a retry");
    }
    if (!nextRetryAt.isAfter(now)) {
      throw new IllegalArgumentException("Next retry time must be after the scheduling time");
    }

    status = ImportStatus.RETRY_SCHEDULED;
    this.failure = Optional.of(failure);
    this.nextRetryAt = Optional.of(nextRetryAt);
  }

  public void complete(Instant now) {
    Objects.requireNonNull(now, "Completion timestamp cannot be null");
    requireStatus(ImportStatus.RUNNING);
    requireNotBeforeStarted(now);

    status = statistics.rejected() == 0 ? ImportStatus.COMPLETED : ImportStatus.PARTIALLY_COMPLETED;
    finishedAt = Optional.of(now);
  }

  public void fail(ImportFailure failure, Instant now) {
    Objects.requireNonNull(failure, "Import failure cannot be null");
    Objects.requireNonNull(now, "Failure timestamp cannot be null");
    requireStatus(ImportStatus.RUNNING, ImportStatus.RETRY_SCHEDULED);
    requireNotBeforeStarted(now);

    status = ImportStatus.FAILED;
    this.failure = Optional.of(failure);
    nextRetryAt = Optional.empty();
    finishedAt = Optional.of(now);
  }

  public void requestCancellation(Instant now) {
    Objects.requireNonNull(now, "Cancellation timestamp cannot be null");
    requireStatus(
        ImportStatus.REQUESTED,
        ImportStatus.RUNNING,
        ImportStatus.RETRY_SCHEDULED,
        ImportStatus.CANCELLING);
    requireNotBeforeRequested(now);
    startedAt.ifPresent(started -> requireNotBefore(now, started, "Cancellation"));

    if (status == ImportStatus.CANCELLING) {
      return;
    }
    failure = Optional.empty();
    nextRetryAt = Optional.empty();
    if (status == ImportStatus.REQUESTED || status == ImportStatus.RETRY_SCHEDULED) {
      status = ImportStatus.CANCELLED;
      finishedAt = Optional.of(now);
      return;
    }
    status = ImportStatus.CANCELLING;
  }

  public void confirmCancellation(Instant now) {
    Objects.requireNonNull(now, "Cancellation timestamp cannot be null");
    requireStatus(ImportStatus.CANCELLING);
    requireNotBeforeStarted(now);
    status = ImportStatus.CANCELLED;
    finishedAt = Optional.of(now);
  }

  private void validateState() {
    if (attemptCount < 0) {
      throw new IllegalArgumentException("Import attempt count cannot be negative");
    }
    if (version < 0) {
      throw new IllegalArgumentException("Import run version cannot be negative");
    }
    startedAt.ifPresent(
        timestamp -> {
          if (timestamp.isBefore(requestedAt)) {
            throw new IllegalArgumentException("Import start cannot precede its request");
          }
        });
    finishedAt.ifPresent(
        timestamp -> {
          if (timestamp.isBefore(requestedAt)) {
            throw new IllegalArgumentException("Import finish cannot precede its request");
          }
        });
    if (startedAt.isPresent() && finishedAt.isPresent()) {
      requireNotBefore(finishedAt.orElseThrow(), startedAt.orElseThrow(), "Import finish");
    }
    if (startedAt.isPresent() && nextRetryAt.isPresent()) {
      if (!nextRetryAt.orElseThrow().isAfter(startedAt.orElseThrow())) {
        throw new IllegalArgumentException("Next retry must be after the import start");
      }
    }

    switch (status) {
      case REQUESTED ->
          requireState(
              attemptCount == 0
                  && statistics.equals(ImportStatistics.ZERO)
                  && startedAt.isEmpty()
                  && finishedAt.isEmpty()
                  && failure.isEmpty()
                  && nextRetryAt.isEmpty());
      case RUNNING, CANCELLING ->
          requireState(
              attemptCount > 0
                  && startedAt.isPresent()
                  && finishedAt.isEmpty()
                  && failure.isEmpty()
                  && nextRetryAt.isEmpty());
      case RETRY_SCHEDULED ->
          requireState(
              attemptCount > 0
                  && startedAt.isPresent()
                  && finishedAt.isEmpty()
                  && failure.filter(ImportFailure::retryable).isPresent()
                  && nextRetryAt.isPresent());
      case COMPLETED ->
          requireState(
              attemptCount > 0
                  && startedAt.isPresent()
                  && finishedAt.isPresent()
                  && statistics.rejected() == 0
                  && failure.isEmpty()
                  && nextRetryAt.isEmpty());
      case PARTIALLY_COMPLETED ->
          requireState(
              attemptCount > 0
                  && startedAt.isPresent()
                  && finishedAt.isPresent()
                  && statistics.rejected() > 0
                  && failure.isEmpty()
                  && nextRetryAt.isEmpty());
      case FAILED ->
          requireState(
              attemptCount > 0
                  && startedAt.isPresent()
                  && finishedAt.isPresent()
                  && failure.isPresent()
                  && nextRetryAt.isEmpty());
      case CANCELLED ->
          requireState(
              finishedAt.isPresent()
                  && failure.isEmpty()
                  && nextRetryAt.isEmpty()
                  && ((attemptCount == 0
                          && statistics.equals(ImportStatistics.ZERO)
                          && startedAt.isEmpty())
                      || (attemptCount > 0 && startedAt.isPresent())));
    }
  }

  private void requireState(boolean valid) {
    if (!valid) {
      throw new IllegalArgumentException("Import run state is inconsistent with its status");
    }
  }

  private void requireStatus(ImportStatus... allowedStatuses) {
    for (ImportStatus allowedStatus : allowedStatuses) {
      if (status == allowedStatus) {
        return;
      }
    }
    throw new IllegalStateException("Import run cannot transition from status " + status);
  }

  private void requireNotBeforeRequested(Instant timestamp) {
    requireNotBefore(timestamp, requestedAt, "Import transition");
  }

  private void requireNotBeforeStarted(Instant timestamp) {
    requireNotBefore(timestamp, startedAt.orElseThrow(), "Import transition");
  }

  private static void requireNotBefore(
      Instant timestamp, Instant lowerBound, String transitionName) {
    if (timestamp.isBefore(lowerBound)) {
      throw new IllegalArgumentException(transitionName + " cannot precede the current lifecycle");
    }
  }

  public ImportRunId id() {
    return id;
  }

  public ConnectorTenantId tenantId() {
    return tenantId;
  }

  public ConnectorId connectorId() {
    return connectorId;
  }

  public ImportType importType() {
    return importType;
  }

  public ImportMode mode() {
    return mode;
  }

  public ImportStatus status() {
    return status;
  }

  public Optional<ImportCursor> committedCursor() {
    return committedCursor;
  }

  public ImportStatistics statistics() {
    return statistics;
  }

  public Optional<ImportFailure> failure() {
    return failure;
  }

  public int attemptCount() {
    return attemptCount;
  }

  public Instant requestedAt() {
    return requestedAt;
  }

  public Optional<Instant> startedAt() {
    return startedAt;
  }

  public Optional<Instant> finishedAt() {
    return finishedAt;
  }

  public Optional<Instant> nextRetryAt() {
    return nextRetryAt;
  }

  public long version() {
    return version;
  }
}
