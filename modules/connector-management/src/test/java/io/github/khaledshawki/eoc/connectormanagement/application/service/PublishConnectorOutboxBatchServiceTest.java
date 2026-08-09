package io.github.khaledshawki.eoc.connectormanagement.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.khaledshawki.eoc.connectormanagement.application.exception.ConnectorEventPublicationException;
import io.github.khaledshawki.eoc.connectormanagement.application.model.event.ConnectorIntegrationEventEnvelope;
import io.github.khaledshawki.eoc.connectormanagement.application.model.outbox.ClaimedConnectorOutboxEvent;
import io.github.khaledshawki.eoc.connectormanagement.application.model.outbox.ConnectorOutboxClaim;
import io.github.khaledshawki.eoc.connectormanagement.application.model.outbox.ConnectorOutboxPublicationFailure;
import io.github.khaledshawki.eoc.connectormanagement.application.model.outbox.ConnectorOutboxPublicationPolicy;
import io.github.khaledshawki.eoc.connectormanagement.application.model.outbox.ConnectorOutboxPublicationRetry;
import io.github.khaledshawki.eoc.connectormanagement.application.model.outbox.ConnectorOutboxPublicationSuccess;
import io.github.khaledshawki.eoc.connectormanagement.application.model.outbox.PublishConnectorOutboxBatchCommand;
import io.github.khaledshawki.eoc.connectormanagement.application.model.outbox.PublishConnectorOutboxBatchResult;
import io.github.khaledshawki.eoc.connectormanagement.application.port.out.ConnectorIntegrationEventPublisher;
import io.github.khaledshawki.eoc.connectormanagement.application.port.out.ConnectorOutboxRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PublishConnectorOutboxBatchServiceTest {

  private static final Instant NOW = Instant.parse("2026-08-03T18:00:00Z");
  private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
  private static final String WORKER = "platform-outbox-1";

  @Test
  void shouldPublishAndAcknowledgeEveryClaimedEvent() {
    RecordingRepository repository = new RecordingRepository(List.of(event(1, 1), event(2, 1)));
    List<ConnectorIntegrationEventEnvelope> published = new ArrayList<>();
    PublishConnectorOutboxBatchService service = service(repository, published::add, 3);

    PublishConnectorOutboxBatchResult result = service.publishBatch(command());

    assertEquals(new PublishConnectorOutboxBatchResult(2, 2, 0, 0), result);
    assertEquals(
        List.of(event(1, 1).integrationEvent(), event(2, 1).integrationEvent()), published);
    assertEquals(List.of(eventId(1), eventId(2)), repository.successIds());
    assertEquals(List.of(1, 1), repository.successAttempts());
    assertTrue(repository.retries.isEmpty());
    assertTrue(repository.failures.isEmpty());
  }

  @Test
  void shouldScheduleARetryForARetryablePublicationFailureWithinTheAttemptBudget() {
    RecordingRepository repository = new RecordingRepository(List.of(event(1, 1)));
    PublishConnectorOutboxBatchService service =
        service(
            repository,
            event -> {
              throw new ConnectorEventPublicationException(
                  "broker-unavailable", true, new IllegalStateException("unavailable"));
            },
            3);

    PublishConnectorOutboxBatchResult result = service.publishBatch(command());

    assertEquals(new PublishConnectorOutboxBatchResult(1, 0, 1, 0), result);
    ConnectorOutboxPublicationRetry retry = repository.retries.getFirst();
    assertEquals(eventId(1), retry.eventId());
    assertEquals("broker-unavailable", retry.failureCode());
    assertEquals(1, retry.publicationAttempt());
    assertEquals(NOW.plusSeconds(30), retry.nextPublishAt());
    assertTrue(repository.successes.isEmpty());
    assertTrue(repository.failures.isEmpty());
  }

  @Test
  void shouldFailPermanentAndExhaustedPublicationAttempts() {
    RecordingRepository repository = new RecordingRepository(List.of(event(1, 1), event(2, 3)));
    PublishConnectorOutboxBatchService service =
        service(
            repository,
            event -> {
              if (event.eventId().equals(eventId(1))) {
                throw new ConnectorEventPublicationException(
                    "contract-rejected", false, new IllegalArgumentException("rejected"));
              }
              throw new ConnectorEventPublicationException(
                  "broker-unavailable", true, new IllegalStateException("unavailable"));
            },
            3);

    PublishConnectorOutboxBatchResult result = service.publishBatch(command());

    assertEquals(new PublishConnectorOutboxBatchResult(2, 0, 0, 2), result);
    assertEquals(List.of(eventId(1), eventId(2)), repository.failureIds());
    assertEquals(List.of(1, 3), repository.failureAttempts());
    assertTrue(repository.retries.isEmpty());
  }

  @Test
  void shouldLeaveTheClaimUnresolvedWhenAnUnexpectedFailureInterruptsTheWorker() {
    RecordingRepository repository = new RecordingRepository(List.of(event(1, 1)));
    PublishConnectorOutboxBatchService service =
        service(
            repository,
            event -> {
              throw new IllegalStateException("simulated-process-crash");
            },
            3);

    assertThrows(IllegalStateException.class, () -> service.publishBatch(command()));

    assertTrue(repository.successes.isEmpty());
    assertTrue(repository.retries.isEmpty());
    assertTrue(repository.failures.isEmpty());
  }

  @Test
  void shouldReturnAnEmptyResultWhenNothingIsPublishable() {
    RecordingRepository repository = new RecordingRepository(List.of());
    PublishConnectorOutboxBatchService service = service(repository, event -> {}, 3);

    assertEquals(PublishConnectorOutboxBatchResult.empty(), service.publishBatch(command()));
  }

  private static PublishConnectorOutboxBatchService service(
      ConnectorOutboxRepository repository,
      ConnectorIntegrationEventPublisher publisher,
      int maxAttempts) {
    return new PublishConnectorOutboxBatchService(
        repository,
        publisher,
        new ConnectorOutboxPublicationPolicy(maxAttempts, Duration.ofSeconds(30)),
        CLOCK);
  }

  private static PublishConnectorOutboxBatchCommand command() {
    return new PublishConnectorOutboxBatchCommand(WORKER, 10, Duration.ofMinutes(1));
  }

  private static ClaimedConnectorOutboxEvent event(long sequence, int attempt) {
    return new ClaimedConnectorOutboxEvent(
        eventId(sequence),
        "connector.import-run.completed.v1",
        1,
        UUID.fromString("00000000-0000-0000-0000-000000000010"),
        "IMPORT_RUN",
        new UUID(0L, 100 + sequence),
        "{\"status\":\"COMPLETED\"}",
        NOW.minusSeconds(1),
        attempt,
        WORKER,
        NOW);
  }

  private static UUID eventId(long sequence) {
    return new UUID(0L, sequence);
  }

  private static final class RecordingRepository implements ConnectorOutboxRepository {

    private final List<ClaimedConnectorOutboxEvent> claimed;
    private final List<ConnectorOutboxPublicationSuccess> successes = new ArrayList<>();
    private final List<ConnectorOutboxPublicationRetry> retries = new ArrayList<>();
    private final List<ConnectorOutboxPublicationFailure> failures = new ArrayList<>();

    private RecordingRepository(List<ClaimedConnectorOutboxEvent> claimed) {
      this.claimed = claimed;
    }

    @Override
    public List<ClaimedConnectorOutboxEvent> claimPublishable(ConnectorOutboxClaim claim) {
      assertEquals(WORKER, claim.claimOwner());
      assertEquals(NOW, claim.claimedAt());
      return claimed;
    }

    @Override
    public void markPublished(ConnectorOutboxPublicationSuccess success) {
      successes.add(success);
    }

    @Override
    public void scheduleRetry(ConnectorOutboxPublicationRetry retry) {
      retries.add(retry);
    }

    @Override
    public void markFailed(ConnectorOutboxPublicationFailure failure) {
      failures.add(failure);
    }

    private List<UUID> successIds() {
      return successes.stream().map(ConnectorOutboxPublicationSuccess::eventId).toList();
    }

    private List<Integer> successAttempts() {
      return successes.stream().map(ConnectorOutboxPublicationSuccess::publicationAttempt).toList();
    }

    private List<UUID> failureIds() {
      return failures.stream().map(ConnectorOutboxPublicationFailure::eventId).toList();
    }

    private List<Integer> failureAttempts() {
      return failures.stream().map(ConnectorOutboxPublicationFailure::publicationAttempt).toList();
    }
  }
}
