package io.github.khaledshawki.eoc.connectormanagement.application.service;

import io.github.khaledshawki.eoc.connectormanagement.application.exception.ConnectorEventPublicationException;
import io.github.khaledshawki.eoc.connectormanagement.application.model.outbox.ClaimedConnectorOutboxEvent;
import io.github.khaledshawki.eoc.connectormanagement.application.model.outbox.ConnectorOutboxClaim;
import io.github.khaledshawki.eoc.connectormanagement.application.model.outbox.ConnectorOutboxPublicationFailure;
import io.github.khaledshawki.eoc.connectormanagement.application.model.outbox.ConnectorOutboxPublicationPolicy;
import io.github.khaledshawki.eoc.connectormanagement.application.model.outbox.ConnectorOutboxPublicationRetry;
import io.github.khaledshawki.eoc.connectormanagement.application.model.outbox.ConnectorOutboxPublicationSuccess;
import io.github.khaledshawki.eoc.connectormanagement.application.model.outbox.PublishConnectorOutboxBatchCommand;
import io.github.khaledshawki.eoc.connectormanagement.application.model.outbox.PublishConnectorOutboxBatchResult;
import io.github.khaledshawki.eoc.connectormanagement.application.port.in.PublishConnectorOutboxBatchUseCase;
import io.github.khaledshawki.eoc.connectormanagement.application.port.out.ConnectorIntegrationEventPublisher;
import io.github.khaledshawki.eoc.connectormanagement.application.port.out.ConnectorOutboxRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

public final class PublishConnectorOutboxBatchService
    implements PublishConnectorOutboxBatchUseCase {

  private final ConnectorOutboxRepository outboxRepository;
  private final ConnectorIntegrationEventPublisher eventPublisher;
  private final ConnectorOutboxPublicationPolicy publicationPolicy;
  private final Clock clock;

  public PublishConnectorOutboxBatchService(
      ConnectorOutboxRepository outboxRepository,
      ConnectorIntegrationEventPublisher eventPublisher,
      ConnectorOutboxPublicationPolicy publicationPolicy,
      Clock clock) {
    this.outboxRepository =
        Objects.requireNonNull(outboxRepository, "Connector outbox repository cannot be null");
    this.eventPublisher =
        Objects.requireNonNull(eventPublisher, "Connector event publisher cannot be null");
    this.publicationPolicy =
        Objects.requireNonNull(publicationPolicy, "Outbox publication policy cannot be null");
    this.clock = Objects.requireNonNull(clock, "Clock cannot be null");
  }

  @Override
  public PublishConnectorOutboxBatchResult publishBatch(
      PublishConnectorOutboxBatchCommand command) {
    Objects.requireNonNull(command, "Outbox publication command cannot be null");
    Instant claimedAt = clock.instant();
    List<ClaimedConnectorOutboxEvent> claimed =
        outboxRepository.claimPublishable(
            new ConnectorOutboxClaim(
                command.workerId(), command.batchSize(), claimedAt, command.claimLease()));
    if (claimed.isEmpty()) {
      return PublishConnectorOutboxBatchResult.empty();
    }

    int published = 0;
    int retriesScheduled = 0;
    int failed = 0;
    for (ClaimedConnectorOutboxEvent event : claimed) {
      try {
        eventPublisher.publish(event);
      } catch (ConnectorEventPublicationException exception) {
        Instant recordedAt = clock.instant();
        if (exception.retryable() && event.publicationAttempt() < publicationPolicy.maxAttempts()) {
          outboxRepository.scheduleRetry(
              new ConnectorOutboxPublicationRetry(
                  event.eventId(),
                  event.claimOwner(),
                  event.publicationAttempt(),
                  exception.failureCode(),
                  publicationPolicy.nextRetryAt(recordedAt),
                  recordedAt));
          retriesScheduled++;
        } else {
          outboxRepository.markFailed(
              new ConnectorOutboxPublicationFailure(
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
          new ConnectorOutboxPublicationSuccess(
              event.eventId(), event.claimOwner(), event.publicationAttempt(), clock.instant()));
      published++;
    }

    return new PublishConnectorOutboxBatchResult(
        claimed.size(), published, retriesScheduled, failed);
  }
}
