package io.github.khaledshawki.eoc.operations.application.service;

import io.github.khaledshawki.eoc.operations.application.exception.OperationsEventPublicationException;
import io.github.khaledshawki.eoc.operations.application.model.outbox.ClaimedOperationsOutboxEvent;
import io.github.khaledshawki.eoc.operations.application.model.outbox.OperationsOutboxClaim;
import io.github.khaledshawki.eoc.operations.application.model.outbox.OperationsOutboxPublicationFailure;
import io.github.khaledshawki.eoc.operations.application.model.outbox.OperationsOutboxPublicationPolicy;
import io.github.khaledshawki.eoc.operations.application.model.outbox.OperationsOutboxPublicationRetry;
import io.github.khaledshawki.eoc.operations.application.model.outbox.OperationsOutboxPublicationSuccess;
import io.github.khaledshawki.eoc.operations.application.model.outbox.PublishOperationsOutboxBatchCommand;
import io.github.khaledshawki.eoc.operations.application.model.outbox.PublishOperationsOutboxBatchResult;
import io.github.khaledshawki.eoc.operations.application.port.in.PublishOperationsOutboxBatchUseCase;
import io.github.khaledshawki.eoc.operations.application.port.out.OperationsIntegrationEventPublisher;
import io.github.khaledshawki.eoc.operations.application.port.out.OperationsOutboxRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

public final class PublishOperationsOutboxBatchService
    implements PublishOperationsOutboxBatchUseCase {

  private final OperationsOutboxRepository outboxRepository;
  private final OperationsIntegrationEventPublisher eventPublisher;
  private final OperationsOutboxPublicationPolicy publicationPolicy;
  private final Clock clock;

  public PublishOperationsOutboxBatchService(
      OperationsOutboxRepository outboxRepository,
      OperationsIntegrationEventPublisher eventPublisher,
      OperationsOutboxPublicationPolicy publicationPolicy,
      Clock clock) {
    this.outboxRepository =
        Objects.requireNonNull(outboxRepository, "Operations outbox repository cannot be null");
    this.eventPublisher =
        Objects.requireNonNull(eventPublisher, "Operations event publisher cannot be null");
    this.publicationPolicy =
        Objects.requireNonNull(publicationPolicy, "Outbox publication policy cannot be null");
    this.clock = Objects.requireNonNull(clock, "Clock cannot be null");
  }

  @Override
  public PublishOperationsOutboxBatchResult publishBatch(
      PublishOperationsOutboxBatchCommand command) {
    Objects.requireNonNull(command, "Outbox publication command cannot be null");
    Instant claimedAt = clock.instant();
    List<ClaimedOperationsOutboxEvent> claimed =
        outboxRepository.claimPublishable(
            new OperationsOutboxClaim(
                command.workerId(), command.batchSize(), claimedAt, command.claimLease()));
    if (claimed.isEmpty()) {
      return PublishOperationsOutboxBatchResult.empty();
    }

    int published = 0;
    int retriesScheduled = 0;
    int failed = 0;
    for (ClaimedOperationsOutboxEvent event : claimed) {
      try {
        eventPublisher.publish(event.integrationEvent());
      } catch (OperationsEventPublicationException exception) {
        Instant recordedAt = clock.instant();
        if (exception.retryable() && event.generationAttempt() < publicationPolicy.maxAttempts()) {
          outboxRepository.scheduleRetry(
              new OperationsOutboxPublicationRetry(
                  event.eventId(),
                  event.claimOwner(),
                  event.publicationAttempt(),
                  exception.failureCode(),
                  publicationPolicy.nextRetryAt(recordedAt),
                  recordedAt));
          retriesScheduled++;
        } else {
          outboxRepository.markFailed(
              new OperationsOutboxPublicationFailure(
                  event.eventId(),
                  event.claimOwner(),
                  event.publicationAttempt(),
                  exception.failureCode(),
                  recordedAt));
          failed++;
        }
        continue;
      }

      outboxRepository.markPublished(
          new OperationsOutboxPublicationSuccess(
              event.eventId(), event.claimOwner(), event.publicationAttempt(), clock.instant()));
      published++;
    }

    return new PublishOperationsOutboxBatchResult(
        claimed.size(), published, retriesScheduled, failed);
  }
}
