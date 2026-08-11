package io.github.khaledshawki.eoc.connectormanagement.application.service;

import io.github.khaledshawki.eoc.connectormanagement.application.exception.ConnectorEventPublicationException;
import io.github.khaledshawki.eoc.connectormanagement.application.model.recovery.ClaimedConnectorDeadLetterReplay;
import io.github.khaledshawki.eoc.connectormanagement.application.model.recovery.ConnectorDeadLetterReplayClaim;
import io.github.khaledshawki.eoc.connectormanagement.application.model.recovery.ConnectorDeadLetterReplayFailure;
import io.github.khaledshawki.eoc.connectormanagement.application.model.recovery.ConnectorDeadLetterReplayPolicy;
import io.github.khaledshawki.eoc.connectormanagement.application.model.recovery.ConnectorDeadLetterReplayRetry;
import io.github.khaledshawki.eoc.connectormanagement.application.model.recovery.ConnectorDeadLetterReplaySuccess;
import io.github.khaledshawki.eoc.connectormanagement.application.model.recovery.PublishConnectorDeadLetterReplayBatchCommand;
import io.github.khaledshawki.eoc.connectormanagement.application.model.recovery.PublishConnectorDeadLetterReplayBatchResult;
import io.github.khaledshawki.eoc.connectormanagement.application.port.in.PublishConnectorDeadLetterReplayBatchUseCase;
import io.github.khaledshawki.eoc.connectormanagement.application.port.out.ConnectorDeadLetterReplayPublisher;
import io.github.khaledshawki.eoc.connectormanagement.application.port.out.ConnectorDeadLetterReplayRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

public final class PublishConnectorDeadLetterReplayBatchService
    implements PublishConnectorDeadLetterReplayBatchUseCase {

  static final String UNKNOWN_PUBLICATION_FAILURE = "connector-dead-letter-replay-unknown";

  private final ConnectorDeadLetterReplayRepository repository;
  private final ConnectorDeadLetterReplayPublisher publisher;
  private final ConnectorDeadLetterReplayPolicy policy;
  private final Clock clock;

  public PublishConnectorDeadLetterReplayBatchService(
      ConnectorDeadLetterReplayRepository repository,
      ConnectorDeadLetterReplayPublisher publisher,
      ConnectorDeadLetterReplayPolicy policy,
      Clock clock) {
    this.repository =
        Objects.requireNonNull(repository, "Connector replay repository cannot be null");
    this.publisher = Objects.requireNonNull(publisher, "Connector replay publisher cannot be null");
    this.policy = Objects.requireNonNull(policy, "Connector replay policy cannot be null");
    this.clock = Objects.requireNonNull(clock, "Clock cannot be null");
  }

  @Override
  public PublishConnectorDeadLetterReplayBatchResult publishBatch(
      PublishConnectorDeadLetterReplayBatchCommand command) {
    Objects.requireNonNull(command, "Replay publication command cannot be null");
    Instant claimedAt = clock.instant();
    List<ClaimedConnectorDeadLetterReplay> claimed =
        repository.claimPublishable(
            new ConnectorDeadLetterReplayClaim(
                command.workerId(), command.batchSize(), claimedAt, command.claimLease()));
    if (claimed.isEmpty()) {
      return PublishConnectorDeadLetterReplayBatchResult.empty();
    }

    int replayed = 0;
    int retriesScheduled = 0;
    int failed = 0;
    for (ClaimedConnectorDeadLetterReplay replay : claimed) {
      try {
        publisher.publish(replay);
      } catch (ConnectorEventPublicationException exception) {
        if (recordFailure(replay, exception.failureCode(), exception.retryable())) {
          retriesScheduled++;
        } else {
          failed++;
        }
        continue;
      } catch (RuntimeException exception) {
        repository.markFailed(
            new ConnectorDeadLetterReplayFailure(
                replay.requestId(),
                replay.claimOwner(),
                replay.publicationAttempt(),
                UNKNOWN_PUBLICATION_FAILURE,
                clock.instant()));
        failed++;
        continue;
      }

      repository.markReplayed(
          new ConnectorDeadLetterReplaySuccess(
              replay.requestId(),
              replay.claimOwner(),
              replay.publicationAttempt(),
              clock.instant()));
      replayed++;
    }
    return new PublishConnectorDeadLetterReplayBatchResult(
        claimed.size(), replayed, retriesScheduled, failed);
  }

  private boolean recordFailure(
      ClaimedConnectorDeadLetterReplay replay, String failureCode, boolean retryable) {
    Instant recordedAt = clock.instant();
    if (retryable && replay.publicationAttempt() < policy.maxAttempts()) {
      repository.scheduleRetry(
          new ConnectorDeadLetterReplayRetry(
              replay.requestId(),
              replay.claimOwner(),
              replay.publicationAttempt(),
              failureCode,
              policy.nextAttemptAt(recordedAt),
              recordedAt));
      return true;
    }
    repository.markFailed(
        new ConnectorDeadLetterReplayFailure(
            replay.requestId(),
            replay.claimOwner(),
            replay.publicationAttempt(),
            failureCode,
            recordedAt));
    return false;
  }
}
