package io.github.khaledshawki.eoc.operations.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.khaledshawki.eoc.operations.application.exception.OperationsEventPublicationException;
import io.github.khaledshawki.eoc.operations.application.model.event.OperationsIntegrationEventEnvelope;
import io.github.khaledshawki.eoc.operations.application.model.outbox.ClaimedOperationsOutboxEvent;
import io.github.khaledshawki.eoc.operations.application.model.outbox.OperationsOutboxClaim;
import io.github.khaledshawki.eoc.operations.application.model.outbox.OperationsOutboxPublicationFailure;
import io.github.khaledshawki.eoc.operations.application.model.outbox.OperationsOutboxPublicationPolicy;
import io.github.khaledshawki.eoc.operations.application.model.outbox.OperationsOutboxPublicationRetry;
import io.github.khaledshawki.eoc.operations.application.model.outbox.OperationsOutboxPublicationSuccess;
import io.github.khaledshawki.eoc.operations.application.model.outbox.PublishOperationsOutboxBatchCommand;
import io.github.khaledshawki.eoc.operations.application.model.outbox.PublishOperationsOutboxBatchResult;
import io.github.khaledshawki.eoc.operations.application.port.out.OperationsIntegrationEventPublisher;
import io.github.khaledshawki.eoc.operations.application.port.out.OperationsOutboxRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PublishOperationsOutboxBatchServiceTest {

  private static final Instant NOW = Instant.parse("2026-08-11T12:00:00Z");
  private static final UUID EVENT_ID = UUID.fromString("00000000-0000-0000-0000-000000000501");
  private static final UUID TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000502");
  private static final UUID AGGREGATE_ID = UUID.fromString("00000000-0000-0000-0000-000000000503");
  private static final PublishOperationsOutboxBatchCommand COMMAND =
      new PublishOperationsOutboxBatchCommand("worker-1", 10, Duration.ofSeconds(30));

  @Test
  void shouldReturnEmptyResultWithoutCallingPublisher() {
    RecordingRepository repository = new RecordingRepository(List.of());
    OperationsIntegrationEventPublisher publisher =
        event -> {
          throw new AssertionError("Publisher must not be called");
        };

    PublishOperationsOutboxBatchResult result =
        service(repository, publisher, 3).publishBatch(COMMAND);

    assertEquals(PublishOperationsOutboxBatchResult.empty(), result);
    assertEquals("worker-1", repository.claim.claimOwner());
    assertEquals(NOW, repository.claim.claimedAt());
  }

  @Test
  void shouldPublishAndFenceTheSuccessfulOutcome() {
    ClaimedOperationsOutboxEvent claimed = claimed(1);
    RecordingRepository repository = new RecordingRepository(List.of(claimed));
    List<OperationsIntegrationEventEnvelope> published = new ArrayList<>();

    PublishOperationsOutboxBatchResult result =
        service(repository, published::add, 3).publishBatch(COMMAND);

    assertEquals(new PublishOperationsOutboxBatchResult(1, 1, 0, 0), result);
    assertEquals(1, published.size());
    assertEquals(7, published.getFirst().aggregateVersion());
    OperationsOutboxPublicationSuccess success = repository.successes.getFirst();
    assertEquals(EVENT_ID, success.eventId());
    assertEquals("worker-1", success.claimOwner());
    assertEquals(1, success.publicationAttempt());
  }

  @Test
  void shouldScheduleBoundedRetryForClassifiedTransientFailure() {
    RecordingRepository repository = new RecordingRepository(List.of(claimed(1)));
    OperationsEventPublicationException failure =
        new OperationsEventPublicationException(
            "broker-unavailable", true, new IllegalStateException("offline"));

    PublishOperationsOutboxBatchResult result =
        service(
                repository,
                event -> {
                  throw failure;
                },
                3)
            .publishBatch(COMMAND);

    assertEquals(new PublishOperationsOutboxBatchResult(1, 0, 1, 0), result);
    OperationsOutboxPublicationRetry retry = repository.retries.getFirst();
    assertEquals(EVENT_ID, retry.eventId());
    assertEquals("broker-unavailable", retry.failureCode());
    assertEquals(NOW.plusSeconds(60), retry.nextPublishAt());
  }

  @Test
  void shouldFailTerminallyForNonRetryableOrExhaustedFailure() {
    for (TestCase testCase : List.of(new TestCase(false, 1), new TestCase(true, 3))) {
      RecordingRepository repository =
          new RecordingRepository(List.of(claimed(testCase.publicationAttempt())));

      PublishOperationsOutboxBatchResult result =
          service(
                  repository,
                  event -> {
                    throw new OperationsEventPublicationException(
                        "invalid-contract", testCase.retryable(), null);
                  },
                  3)
              .publishBatch(COMMAND);

      assertEquals(new PublishOperationsOutboxBatchResult(1, 0, 0, 1), result);
      OperationsOutboxPublicationFailure failure = repository.failures.getFirst();
      assertEquals(testCase.publicationAttempt(), failure.publicationAttempt());
      assertTrue(repository.retries.isEmpty());
    }
  }

  @Test
  void shouldPropagateUnknownPublisherFailureWithoutRecordingFalseOutcome() {
    RecordingRepository repository = new RecordingRepository(List.of(claimed(1)));
    IllegalStateException failure = new IllegalStateException("unexpected");

    IllegalStateException thrown =
        assertThrows(
            IllegalStateException.class,
            () ->
                service(
                        repository,
                        event -> {
                          throw failure;
                        },
                        3)
                    .publishBatch(COMMAND));

    assertSame(failure, thrown);
    assertTrue(repository.successes.isEmpty());
    assertTrue(repository.retries.isEmpty());
    assertTrue(repository.failures.isEmpty());
  }

  private static PublishOperationsOutboxBatchService service(
      OperationsOutboxRepository repository,
      OperationsIntegrationEventPublisher publisher,
      int maxAttempts) {
    return new PublishOperationsOutboxBatchService(
        repository,
        publisher,
        new OperationsOutboxPublicationPolicy(maxAttempts, Duration.ofMinutes(1)),
        Clock.fixed(NOW, ZoneOffset.UTC));
  }

  private static ClaimedOperationsOutboxEvent claimed(int publicationAttempt) {
    return new ClaimedOperationsOutboxEvent(
        EVENT_ID,
        "operations.invoice.synchronized.v1",
        1,
        TENANT_ID,
        "INVOICE",
        AGGREGATE_ID,
        7,
        "{}",
        NOW.minusSeconds(1),
        publicationAttempt,
        "worker-1",
        NOW);
  }

  private record TestCase(boolean retryable, int publicationAttempt) {}

  private static final class RecordingRepository implements OperationsOutboxRepository {

    private final List<ClaimedOperationsOutboxEvent> claimedEvents;
    private final List<OperationsOutboxPublicationSuccess> successes = new ArrayList<>();
    private final List<OperationsOutboxPublicationRetry> retries = new ArrayList<>();
    private final List<OperationsOutboxPublicationFailure> failures = new ArrayList<>();
    private OperationsOutboxClaim claim;

    private RecordingRepository(List<ClaimedOperationsOutboxEvent> claimedEvents) {
      this.claimedEvents = claimedEvents;
    }

    @Override
    public List<ClaimedOperationsOutboxEvent> claimPublishable(OperationsOutboxClaim claim) {
      this.claim = claim;
      return claimedEvents;
    }

    @Override
    public void markPublished(OperationsOutboxPublicationSuccess success) {
      successes.add(success);
    }

    @Override
    public void scheduleRetry(OperationsOutboxPublicationRetry retry) {
      retries.add(retry);
    }

    @Override
    public void markFailed(OperationsOutboxPublicationFailure failure) {
      failures.add(failure);
    }
  }
}
