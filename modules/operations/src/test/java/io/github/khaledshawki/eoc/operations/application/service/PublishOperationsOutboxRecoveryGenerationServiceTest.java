package io.github.khaledshawki.eoc.operations.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.khaledshawki.eoc.operations.application.exception.OperationsEventPublicationException;
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

class PublishOperationsOutboxRecoveryGenerationServiceTest {

  private static final Instant NOW = Instant.parse("2026-08-11T14:00:00Z");
  private static final UUID EVENT_ID = UUID.fromString("00000000-0000-0000-0000-000000000801");
  private static final UUID TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000802");
  private static final UUID AGGREGATE_ID = UUID.fromString("00000000-0000-0000-0000-000000000803");

  @Test
  void shouldUseGenerationAttemptBudgetInsteadOfLifetimeAttemptBudget() {
    RecordingRepository repository = new RecordingRepository(claimed(8, 2, 1));

    PublishOperationsOutboxBatchResult result = service(repository).publishBatch(command());

    assertEquals(new PublishOperationsOutboxBatchResult(1, 0, 1, 0), result);
    assertEquals(1, repository.retries.size());
    assertTrue(repository.failures.isEmpty());
    assertEquals(8, repository.retries.getFirst().publicationAttempt());
  }

  @Test
  void shouldStopRetryingWhenTheCurrentRecoveryGenerationIsExhausted() {
    RecordingRepository repository = new RecordingRepository(claimed(10, 3, 3));

    PublishOperationsOutboxBatchResult result = service(repository).publishBatch(command());

    assertEquals(new PublishOperationsOutboxBatchResult(1, 0, 0, 1), result);
    assertTrue(repository.retries.isEmpty());
    assertEquals(10, repository.failures.getFirst().publicationAttempt());
  }

  private static PublishOperationsOutboxBatchService service(RecordingRepository repository) {
    OperationsIntegrationEventPublisher publisher =
        event -> {
          throw new OperationsEventPublicationException(
              "broker-unavailable", true, new IllegalStateException("offline"));
        };
    return new PublishOperationsOutboxBatchService(
        repository,
        publisher,
        new OperationsOutboxPublicationPolicy(3, Duration.ofMinutes(1)),
        Clock.fixed(NOW, ZoneOffset.UTC));
  }

  private static PublishOperationsOutboxBatchCommand command() {
    return new PublishOperationsOutboxBatchCommand("worker-recovery", 1, Duration.ofMinutes(1));
  }

  private static ClaimedOperationsOutboxEvent claimed(
      int publicationAttempt, int recoveryGeneration, int generationAttempt) {
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
        recoveryGeneration,
        generationAttempt,
        "worker-recovery",
        NOW);
  }

  private static final class RecordingRepository implements OperationsOutboxRepository {

    private final ClaimedOperationsOutboxEvent event;
    private final List<OperationsOutboxPublicationRetry> retries = new ArrayList<>();
    private final List<OperationsOutboxPublicationFailure> failures = new ArrayList<>();

    private RecordingRepository(ClaimedOperationsOutboxEvent event) {
      this.event = event;
    }

    @Override
    public List<ClaimedOperationsOutboxEvent> claimPublishable(OperationsOutboxClaim claim) {
      return List.of(event);
    }

    @Override
    public void markPublished(OperationsOutboxPublicationSuccess success) {
      throw new AssertionError("The publisher always fails in this test");
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
