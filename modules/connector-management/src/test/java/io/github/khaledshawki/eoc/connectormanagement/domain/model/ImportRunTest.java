package io.github.khaledshawki.eoc.connectormanagement.domain.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ImportRunTest {

  private static final Instant REQUESTED_AT = Instant.parse("2026-08-02T08:00:00Z");
  private static final Instant STARTED_AT = Instant.parse("2026-08-02T08:01:00Z");
  private static final ConnectorTenantId TENANT_ID =
      ConnectorTenantId.of(UUID.fromString("00000000-0000-0000-0000-000000000010"));
  private static final ConnectorId CONNECTOR_ID =
      ConnectorId.of(UUID.fromString("00000000-0000-0000-0000-000000000020"));

  @Test
  void shouldRequestAnIncrementalRunFromItsDurableStartingCursor() {
    ImportRun run = requestedRun(Optional.of(new ImportCursor("customer-100")));

    assertEquals(ImportStatus.REQUESTED, run.status());
    assertEquals(ImportMode.INCREMENTAL, run.mode());
    assertEquals(Optional.of(new ImportCursor("customer-100")), run.committedCursor());
    assertEquals(ImportStatistics.ZERO, run.statistics());
    assertEquals(0, run.attemptCount());
    assertEquals(0, run.version());
    assertTrue(run.startedAt().isEmpty());
    assertFalse(run.status().terminal());
  }

  @Test
  void shouldStartRequestedAndDueRetryAttempts() {
    ImportRun run = requestedRun(Optional.empty());
    run.start(STARTED_AT);
    run.scheduleRetry(retryableFailure(), STARTED_AT.plusSeconds(60), STARTED_AT.plusSeconds(1));

    assertThrows(IllegalStateException.class, () -> run.start(STARTED_AT.plusSeconds(30)));

    run.start(STARTED_AT.plusSeconds(60));

    assertEquals(ImportStatus.RUNNING, run.status());
    assertEquals(2, run.attemptCount());
    assertEquals(Optional.of(STARTED_AT), run.startedAt());
    assertTrue(run.failure().isEmpty());
    assertTrue(run.nextRetryAt().isEmpty());
  }

  @Test
  void shouldAdvanceTheCursorOnlyWithDurablyAcceptedPageOutcomes() {
    ImportRun run = runningRun(Optional.of(new ImportCursor("customer-100")));
    ImportStatistics page = new ImportStatistics(5, 3, 1, 1);

    run.recordAcceptedPage(
        Optional.of(new ImportCursor("customer-100")),
        Optional.of(new ImportCursor("customer-105")),
        page);

    assertEquals(Optional.of(new ImportCursor("customer-105")), run.committedCursor());
    assertEquals(page, run.statistics());
  }

  @Test
  void shouldRejectStaleAndNonAdvancingPageAcknowledgements() {
    ImportRun run = runningRun(Optional.of(new ImportCursor("customer-100")));

    assertThrows(
        IllegalStateException.class,
        () ->
            run.recordAcceptedPage(
                Optional.of(new ImportCursor("customer-100")),
                Optional.empty(),
                new ImportStatistics(1, 1, 0, 0)));
    assertThrows(
        IllegalStateException.class,
        () ->
            run.recordAcceptedPage(
                Optional.of(new ImportCursor("customer-099")),
                Optional.of(new ImportCursor("customer-105")),
                new ImportStatistics(1, 1, 0, 0)));
    assertThrows(
        IllegalStateException.class,
        () ->
            run.recordAcceptedPage(
                Optional.of(new ImportCursor("customer-100")),
                Optional.of(new ImportCursor("customer-100")),
                new ImportStatistics(1, 1, 0, 0)));

    assertEquals(Optional.of(new ImportCursor("customer-100")), run.committedCursor());
    assertEquals(ImportStatistics.ZERO, run.statistics());
  }

  @Test
  void shouldAccumulateClassifiedStatisticsAndRejectOverflow() {
    ImportStatistics statistics =
        new ImportStatistics(7, 4, 2, 1).plus(new ImportStatistics(3, 2, 0, 1));

    assertEquals(new ImportStatistics(10, 6, 2, 2), statistics);
    assertThrows(IllegalArgumentException.class, () -> new ImportStatistics(2, 1, 0, 0));
    assertThrows(IllegalArgumentException.class, () -> new ImportStatistics(-1, 0, 0, 0));
    assertThrows(
        IllegalStateException.class,
        () ->
            new ImportStatistics(Long.MAX_VALUE, Long.MAX_VALUE, 0, 0)
                .plus(new ImportStatistics(1, 1, 0, 0)));
  }

  @Test
  void shouldCompleteWithAStatusThatReflectsRejectedRecords() {
    ImportRun completed = runningRun(Optional.empty());
    ImportRun partial = runningRun(Optional.empty());
    partial.recordAcceptedPage(
        Optional.empty(),
        Optional.of(new ImportCursor("invoice-2")),
        new ImportStatistics(2, 1, 1, 0));

    completed.complete(STARTED_AT.plusSeconds(30));
    partial.complete(STARTED_AT.plusSeconds(30));

    assertEquals(ImportStatus.COMPLETED, completed.status());
    assertEquals(ImportStatus.PARTIALLY_COMPLETED, partial.status());
    assertTrue(completed.status().terminal());
    assertTrue(partial.status().terminal());
  }

  @Test
  void shouldScheduleOnlyRetryableFailuresInTheFuture() {
    ImportRun run = runningRun(Optional.empty());
    ImportFailure permanent =
        new ImportFailure(ImportFailureCategory.AUTHENTICATION_FAILED, "authentication-failed");

    assertThrows(
        IllegalArgumentException.class,
        () -> run.scheduleRetry(permanent, STARTED_AT.plusSeconds(30), STARTED_AT));
    assertThrows(
        IllegalArgumentException.class,
        () -> run.scheduleRetry(retryableFailure(), STARTED_AT, STARTED_AT));

    run.scheduleRetry(retryableFailure(), STARTED_AT.plusSeconds(30), STARTED_AT);

    assertEquals(ImportStatus.RETRY_SCHEDULED, run.status());
    assertEquals(Optional.of(retryableFailure()), run.failure());
  }

  @Test
  void shouldPersistOnlySanitizedFailureInformation() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new ImportFailure(
                ImportFailureCategory.SOURCE_UNAVAILABLE, "raw response includes credentials"));

    ImportRun run = runningRun(Optional.empty());
    ImportFailure failure =
        new ImportFailure(ImportFailureCategory.AUTHENTICATION_FAILED, "authentication-failed");
    run.fail(failure, STARTED_AT.plusSeconds(10));

    assertEquals(ImportStatus.FAILED, run.status());
    assertEquals(Optional.of(failure), run.failure());
    assertEquals(Optional.of(STARTED_AT.plusSeconds(10)), run.finishedAt());
  }

  @Test
  void shouldCancelQueuedWorkImmediatelyAndRunningWorkCooperatively() {
    ImportRun queued = requestedRun(Optional.of(new ImportCursor("customer-10")));
    ImportRun running = runningRun(Optional.of(new ImportCursor("customer-20")));
    running.recordAcceptedPage(
        Optional.of(new ImportCursor("customer-20")),
        Optional.of(new ImportCursor("customer-21")),
        new ImportStatistics(1, 1, 0, 0));

    queued.requestCancellation(STARTED_AT);
    running.requestCancellation(STARTED_AT.plusSeconds(1));

    assertEquals(ImportStatus.CANCELLED, queued.status());
    assertEquals(ImportStatus.CANCELLING, running.status());
    assertEquals(Optional.of(new ImportCursor("customer-21")), running.committedCursor());
    assertEquals(new ImportStatistics(1, 1, 0, 0), running.statistics());

    running.requestCancellation(STARTED_AT.plusSeconds(2));
    running.confirmCancellation(STARTED_AT.plusSeconds(3));

    assertEquals(ImportStatus.CANCELLED, running.status());
  }

  @Test
  void shouldRejectLifecycleChangesAfterATerminalOutcome() {
    ImportRun run = runningRun(Optional.empty());
    run.complete(STARTED_AT.plusSeconds(1));

    assertThrows(IllegalStateException.class, () -> run.start(STARTED_AT.plusSeconds(2)));
    assertThrows(
        IllegalStateException.class,
        () -> run.recordAcceptedPage(Optional.empty(), Optional.empty(), ImportStatistics.ZERO));
    assertThrows(
        IllegalStateException.class, () -> run.requestCancellation(STARTED_AT.plusSeconds(2)));
  }

  @Test
  void shouldRejectInconsistentReconstitutedState() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            ImportRun.reconstitute(
                ImportRunId.generate(),
                TENANT_ID,
                CONNECTOR_ID,
                ImportType.CUSTOMERS,
                ImportMode.INCREMENTAL,
                ImportStatus.RUNNING,
                Optional.empty(),
                ImportStatistics.ZERO,
                Optional.empty(),
                0,
                REQUESTED_AT,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                0));
  }

  private static ImportRun requestedRun(Optional<ImportCursor> startingCursor) {
    return ImportRun.request(
        TENANT_ID,
        CONNECTOR_ID,
        ImportType.CUSTOMERS,
        ImportMode.INCREMENTAL,
        startingCursor,
        REQUESTED_AT);
  }

  private static ImportRun runningRun(Optional<ImportCursor> startingCursor) {
    ImportRun run = requestedRun(startingCursor);
    run.start(STARTED_AT);
    return run;
  }

  private static ImportFailure retryableFailure() {
    return new ImportFailure(ImportFailureCategory.SOURCE_UNAVAILABLE, "source-unavailable");
  }
}
