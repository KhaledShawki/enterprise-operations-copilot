package io.github.khaledshawki.eoc.connectormanagement.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.khaledshawki.eoc.connectormanagement.application.exception.ConnectorDeadLetterNotFoundException;
import io.github.khaledshawki.eoc.connectormanagement.application.exception.ConnectorDeadLetterReplayLimitExceededException;
import io.github.khaledshawki.eoc.connectormanagement.application.exception.ConnectorDeadLetterReplayNotFoundException;
import io.github.khaledshawki.eoc.connectormanagement.application.model.authorization.ConnectorActor;
import io.github.khaledshawki.eoc.connectormanagement.application.model.recovery.ClaimedConnectorDeadLetterReplay;
import io.github.khaledshawki.eoc.connectormanagement.application.model.recovery.ConnectorDeadLetterPage;
import io.github.khaledshawki.eoc.connectormanagement.application.model.recovery.ConnectorDeadLetterPartition;
import io.github.khaledshawki.eoc.connectormanagement.application.model.recovery.ConnectorDeadLetterRecord;
import io.github.khaledshawki.eoc.connectormanagement.application.model.recovery.ConnectorDeadLetterReference;
import io.github.khaledshawki.eoc.connectormanagement.application.model.recovery.ConnectorDeadLetterReplayClaim;
import io.github.khaledshawki.eoc.connectormanagement.application.model.recovery.ConnectorDeadLetterReplayFailure;
import io.github.khaledshawki.eoc.connectormanagement.application.model.recovery.ConnectorDeadLetterReplayRequest;
import io.github.khaledshawki.eoc.connectormanagement.application.model.recovery.ConnectorDeadLetterReplayRetry;
import io.github.khaledshawki.eoc.connectormanagement.application.model.recovery.ConnectorDeadLetterReplayStatus;
import io.github.khaledshawki.eoc.connectormanagement.application.model.recovery.ConnectorDeadLetterReplaySuccess;
import io.github.khaledshawki.eoc.connectormanagement.application.model.recovery.NewConnectorDeadLetterReplayRequest;
import io.github.khaledshawki.eoc.connectormanagement.application.model.recovery.RequestConnectorDeadLetterReplayCommand;
import io.github.khaledshawki.eoc.connectormanagement.application.port.out.ConnectorDeadLetterReader;
import io.github.khaledshawki.eoc.connectormanagement.application.port.out.ConnectorDeadLetterReplayRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RequestConnectorDeadLetterReplayServiceTest {

  private static final Instant NOW = Instant.parse("2026-08-10T12:00:00Z");
  private static final UUID REQUEST_ID = UUID.fromString("00000000-0000-0000-0000-000000000054");
  private static final ConnectorDeadLetterReference REFERENCE =
      new ConnectorDeadLetterReference(2, 19);

  @Test
  void capturesAnImmutableAuditedReplayRequestAndAdvancesItsGeneration() {
    ConnectorDeadLetterRecord record = record(1);
    RecordingRepository repository = new RecordingRepository();
    RequestConnectorDeadLetterReplayService service = service(Optional.of(record), repository, 3);

    ConnectorDeadLetterReplayRequest result =
        service.request(
            new RequestConnectorDeadLetterReplayCommand(
                new ConnectorActor("https://issuer.example", "admin-42"),
                REFERENCE,
                "contract fixed"));

    assertSame(repository.returned, result);
    assertEquals(2, repository.request.replayGeneration());
    assertEquals(record.fingerprint(), repository.request.recordFingerprint());
    assertEquals("admin-42", repository.request.requestedBySubject());
    assertEquals(NOW, repository.request.requestedAt());
  }

  @Test
  void rejectsMissingRecordsAndReplayLoopsBeforePersistence() {
    RecordingRepository repository = new RecordingRepository();

    assertThrows(
        ConnectorDeadLetterNotFoundException.class,
        () -> service(Optional.empty(), repository, 3).request(command()));
    assertThrows(
        ConnectorDeadLetterReplayLimitExceededException.class,
        () -> service(Optional.of(record(3)), repository, 3).request(command()));
  }

  @Test
  void exposesDurableRequestStatusAndMapsMissingIds() {
    RecordingRepository repository = new RecordingRepository();
    repository.found = Optional.of(repository.returned);
    RequestConnectorDeadLetterReplayService service =
        service(Optional.of(record(0)), repository, 3);

    assertSame(repository.returned, service.get(REQUEST_ID));
    repository.found = Optional.empty();
    assertThrows(ConnectorDeadLetterReplayNotFoundException.class, () -> service.get(REQUEST_ID));
  }

  private static RequestConnectorDeadLetterReplayService service(
      Optional<ConnectorDeadLetterRecord> record,
      RecordingRepository repository,
      int maxGeneration) {
    return new RequestConnectorDeadLetterReplayService(
        new FixedReader(record),
        repository,
        () -> REQUEST_ID,
        Clock.fixed(NOW, ZoneOffset.UTC),
        maxGeneration);
  }

  private static RequestConnectorDeadLetterReplayCommand command() {
    return new RequestConnectorDeadLetterReplayCommand(
        new ConnectorActor("https://issuer.example", "admin-42"), REFERENCE, "contract fixed");
  }

  private static ConnectorDeadLetterRecord record(int generation) {
    return new ConnectorDeadLetterRecord(
        REFERENCE,
        "connector.events.dlt",
        Optional.of("tenant:IMPORT_RUN:id"),
        Optional.of("{}"),
        "connector.events",
        2,
        11,
        NOW.minusSeconds(60),
        "invalid-connector-event-payload",
        false,
        "java.lang.IllegalArgumentException",
        Optional.of("invalid payload"),
        generation,
        List.of());
  }

  private record FixedReader(Optional<ConnectorDeadLetterRecord> record)
      implements ConnectorDeadLetterReader {

    @Override
    public List<ConnectorDeadLetterPartition> listPartitions() {
      return List.of();
    }

    @Override
    public ConnectorDeadLetterPage readPage(int partition, long fromOffset, int limit) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Optional<ConnectorDeadLetterRecord> find(ConnectorDeadLetterReference reference) {
      return record;
    }
  }

  private static final class RecordingRepository implements ConnectorDeadLetterReplayRepository {

    private final ConnectorDeadLetterReplayRequest returned =
        new ConnectorDeadLetterReplayRequest(
            REQUEST_ID,
            REFERENCE,
            ConnectorDeadLetterReplayStatus.PENDING,
            2,
            "https://issuer.example",
            "admin-42",
            "contract fixed",
            NOW,
            0,
            Optional.empty(),
            Optional.empty());
    private NewConnectorDeadLetterReplayRequest request;
    private Optional<ConnectorDeadLetterReplayRequest> found = Optional.empty();

    @Override
    public ConnectorDeadLetterReplayRequest request(NewConnectorDeadLetterReplayRequest request) {
      this.request = request;
      return returned;
    }

    @Override
    public Optional<ConnectorDeadLetterReplayRequest> findById(UUID requestId) {
      return found;
    }

    @Override
    public List<ClaimedConnectorDeadLetterReplay> claimPublishable(
        ConnectorDeadLetterReplayClaim claim) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void markReplayed(ConnectorDeadLetterReplaySuccess success) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void scheduleRetry(ConnectorDeadLetterReplayRetry retry) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void markFailed(ConnectorDeadLetterReplayFailure failure) {
      throw new UnsupportedOperationException();
    }
  }
}
