package io.github.khaledshawki.eoc.connectormanagement.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.khaledshawki.eoc.connectormanagement.application.exception.ConnectorEventPublicationException;
import io.github.khaledshawki.eoc.connectormanagement.application.model.recovery.ClaimedConnectorDeadLetterReplay;
import io.github.khaledshawki.eoc.connectormanagement.application.model.recovery.ConnectorDeadLetterReference;
import io.github.khaledshawki.eoc.connectormanagement.application.model.recovery.ConnectorDeadLetterReplayClaim;
import io.github.khaledshawki.eoc.connectormanagement.application.model.recovery.ConnectorDeadLetterReplayFailure;
import io.github.khaledshawki.eoc.connectormanagement.application.model.recovery.ConnectorDeadLetterReplayPolicy;
import io.github.khaledshawki.eoc.connectormanagement.application.model.recovery.ConnectorDeadLetterReplayRequest;
import io.github.khaledshawki.eoc.connectormanagement.application.model.recovery.ConnectorDeadLetterReplayRetry;
import io.github.khaledshawki.eoc.connectormanagement.application.model.recovery.ConnectorDeadLetterReplaySuccess;
import io.github.khaledshawki.eoc.connectormanagement.application.model.recovery.NewConnectorDeadLetterReplayRequest;
import io.github.khaledshawki.eoc.connectormanagement.application.model.recovery.PublishConnectorDeadLetterReplayBatchCommand;
import io.github.khaledshawki.eoc.connectormanagement.application.model.recovery.PublishConnectorDeadLetterReplayBatchResult;
import io.github.khaledshawki.eoc.connectormanagement.application.port.out.ConnectorDeadLetterReplayPublisher;
import io.github.khaledshawki.eoc.connectormanagement.application.port.out.ConnectorDeadLetterReplayRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PublishConnectorDeadLetterReplayBatchServiceTest {

  private static final Instant NOW = Instant.parse("2026-08-10T12:00:00Z");

  @Test
  void recordsSuccessRetryExhaustionAndUnknownFailureWithClaimFencingEvidence() {
    ClaimedConnectorDeadLetterReplay success = replay(1, 1);
    ClaimedConnectorDeadLetterReplay retry = replay(2, 1);
    ClaimedConnectorDeadLetterReplay exhausted = replay(3, 3);
    ClaimedConnectorDeadLetterReplay unknown = replay(4, 1);
    RecordingRepository repository =
        new RecordingRepository(List.of(success, retry, exhausted, unknown));
    ConnectorDeadLetterReplayPublisher publisher =
        replay -> {
          if (replay == retry) {
            throw new ConnectorEventPublicationException("broker-unavailable", true, null);
          }
          if (replay == exhausted) {
            throw new ConnectorEventPublicationException("broker-unavailable", true, null);
          }
          if (replay == unknown) {
            throw new IllegalStateException("unexpected");
          }
        };
    PublishConnectorDeadLetterReplayBatchService service =
        new PublishConnectorDeadLetterReplayBatchService(
            repository,
            publisher,
            new ConnectorDeadLetterReplayPolicy(3, Duration.ofMinutes(1)),
            Clock.fixed(NOW, ZoneOffset.UTC));

    PublishConnectorDeadLetterReplayBatchResult result =
        service.publishBatch(
            new PublishConnectorDeadLetterReplayBatchCommand(
                "worker-1", 10, Duration.ofSeconds(30)));

    assertEquals(new PublishConnectorDeadLetterReplayBatchResult(4, 1, 1, 2), result);
    assertEquals(1, repository.successes.size());
    assertEquals("worker-1", repository.successes.getFirst().claimOwner());
    assertEquals(NOW.plusSeconds(60), repository.retries.getFirst().nextAttemptAt());
    assertEquals("broker-unavailable", repository.failures.getFirst().failureCode());
    assertEquals(
        PublishConnectorDeadLetterReplayBatchService.UNKNOWN_PUBLICATION_FAILURE,
        repository.failures.get(1).failureCode());
  }

  @Test
  void returnsAnEmptyResultWithoutCallingThePublisher() {
    RecordingRepository repository = new RecordingRepository(List.of());
    PublishConnectorDeadLetterReplayBatchService service =
        new PublishConnectorDeadLetterReplayBatchService(
            repository,
            replay -> {
              throw new AssertionError("publisher must not be called");
            },
            new ConnectorDeadLetterReplayPolicy(3, Duration.ofSeconds(1)),
            Clock.fixed(NOW, ZoneOffset.UTC));

    assertEquals(
        PublishConnectorDeadLetterReplayBatchResult.empty(),
        service.publishBatch(
            new PublishConnectorDeadLetterReplayBatchCommand(
                "worker-1", 1, Duration.ofSeconds(30))));
  }

  private static ClaimedConnectorDeadLetterReplay replay(int offset, int attempt) {
    return new ClaimedConnectorDeadLetterReplay(
        new UUID(0, offset),
        new ConnectorDeadLetterReference(0, offset),
        "connector.events",
        0,
        offset,
        NOW.minusSeconds(10),
        Optional.of("key"),
        Optional.of("{}"),
        List.of(),
        1,
        attempt,
        "worker-1",
        NOW);
  }

  private static final class RecordingRepository implements ConnectorDeadLetterReplayRepository {

    private final List<ClaimedConnectorDeadLetterReplay> claimed;
    private final List<ConnectorDeadLetterReplaySuccess> successes = new ArrayList<>();
    private final List<ConnectorDeadLetterReplayRetry> retries = new ArrayList<>();
    private final List<ConnectorDeadLetterReplayFailure> failures = new ArrayList<>();

    private RecordingRepository(List<ClaimedConnectorDeadLetterReplay> claimed) {
      this.claimed = claimed;
    }

    @Override
    public ConnectorDeadLetterReplayRequest request(NewConnectorDeadLetterReplayRequest request) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Optional<ConnectorDeadLetterReplayRequest> findById(UUID requestId) {
      throw new UnsupportedOperationException();
    }

    @Override
    public List<ClaimedConnectorDeadLetterReplay> claimPublishable(
        ConnectorDeadLetterReplayClaim claim) {
      return claimed;
    }

    @Override
    public void markReplayed(ConnectorDeadLetterReplaySuccess success) {
      successes.add(success);
    }

    @Override
    public void scheduleRetry(ConnectorDeadLetterReplayRetry retry) {
      retries.add(retry);
    }

    @Override
    public void markFailed(ConnectorDeadLetterReplayFailure failure) {
      failures.add(failure);
    }
  }
}
