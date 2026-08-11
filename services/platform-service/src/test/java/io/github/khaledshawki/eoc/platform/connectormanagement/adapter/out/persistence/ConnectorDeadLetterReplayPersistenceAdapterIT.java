package io.github.khaledshawki.eoc.platform.connectormanagement.adapter.out.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.khaledshawki.eoc.connectormanagement.application.exception.ConnectorDeadLetterReplayClaimLostException;
import io.github.khaledshawki.eoc.connectormanagement.application.exception.ConnectorDeadLetterReplayCollisionException;
import io.github.khaledshawki.eoc.connectormanagement.application.model.recovery.ClaimedConnectorDeadLetterReplay;
import io.github.khaledshawki.eoc.connectormanagement.application.model.recovery.ConnectorDeadLetterHeader;
import io.github.khaledshawki.eoc.connectormanagement.application.model.recovery.ConnectorDeadLetterRecord;
import io.github.khaledshawki.eoc.connectormanagement.application.model.recovery.ConnectorDeadLetterReference;
import io.github.khaledshawki.eoc.connectormanagement.application.model.recovery.ConnectorDeadLetterReplayClaim;
import io.github.khaledshawki.eoc.connectormanagement.application.model.recovery.ConnectorDeadLetterReplayRetry;
import io.github.khaledshawki.eoc.connectormanagement.application.model.recovery.ConnectorDeadLetterReplayStatus;
import io.github.khaledshawki.eoc.connectormanagement.application.model.recovery.ConnectorDeadLetterReplaySuccess;
import io.github.khaledshawki.eoc.connectormanagement.application.model.recovery.NewConnectorDeadLetterReplayRequest;
import io.github.khaledshawki.eoc.platform.TestcontainersConfiguration;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class ConnectorDeadLetterReplayPersistenceAdapterIT {

  private static final Instant NOW = Instant.parse("2026-08-10T12:00:00Z");
  private static final UUID REQUEST_ID = UUID.fromString("00000000-0000-0000-0000-000000000054");

  @Autowired private ConnectorDeadLetterReplayPersistenceAdapter repository;
  @Autowired private JdbcTemplate jdbcTemplate;

  @BeforeEach
  void setUp() {
    jdbcTemplate.update("DELETE FROM connector_dead_letter_replay_requests");
  }

  @Test
  void requestIsIdempotentByDltCoordinatesAndRejectsOffsetReuseWithDifferentContent() {
    NewConnectorDeadLetterReplayRequest original = request(REQUEST_ID, record("{}"));

    assertEquals(REQUEST_ID, repository.request(original).requestId());
    assertEquals(
        REQUEST_ID,
        repository
            .request(request(UUID.fromString("00000000-0000-0000-0000-000000000055"), record("{}")))
            .requestId());
    assertEquals(
        1,
        jdbcTemplate.queryForObject(
            "SELECT count(*) FROM connector_dead_letter_replay_requests", Integer.class));

    assertThrows(
        ConnectorDeadLetterReplayCollisionException.class,
        () ->
            repository.request(
                request(
                    UUID.fromString("00000000-0000-0000-0000-000000000056"),
                    record("{\"changed\":true}"))));
  }

  @Test
  void retryLifecycleIsFencedByWorkerAndAttemptAndEventuallyBecomesReplayed() {
    repository.request(request(REQUEST_ID, record("{}")));
    ClaimedConnectorDeadLetterReplay first =
        repository
            .claimPublishable(
                new ConnectorDeadLetterReplayClaim(
                    "worker-a", 1, NOW.plusSeconds(1), Duration.ofSeconds(30)))
            .getFirst();

    assertEquals(1, first.publicationAttempt());
    assertThrows(
        ConnectorDeadLetterReplayClaimLostException.class,
        () ->
            repository.scheduleRetry(
                new ConnectorDeadLetterReplayRetry(
                    REQUEST_ID,
                    "worker-b",
                    1,
                    "broker-unavailable",
                    NOW.plusSeconds(62),
                    NOW.plusSeconds(2))));

    repository.scheduleRetry(
        new ConnectorDeadLetterReplayRetry(
            REQUEST_ID,
            "worker-a",
            1,
            "broker-unavailable",
            NOW.plusSeconds(62),
            NOW.plusSeconds(2)));
    assertTrue(
        repository
            .claimPublishable(
                new ConnectorDeadLetterReplayClaim(
                    "worker-a", 1, NOW.plusSeconds(61), Duration.ofSeconds(30)))
            .isEmpty());

    ClaimedConnectorDeadLetterReplay second =
        repository
            .claimPublishable(
                new ConnectorDeadLetterReplayClaim(
                    "worker-b", 1, NOW.plusSeconds(62), Duration.ofSeconds(30)))
            .getFirst();
    assertEquals(2, second.publicationAttempt());
    repository.markReplayed(
        new ConnectorDeadLetterReplaySuccess(REQUEST_ID, "worker-b", 2, NOW.plusSeconds(63)));

    assertEquals(
        ConnectorDeadLetterReplayStatus.REPLAYED,
        repository.findById(REQUEST_ID).orElseThrow().status());
    assertEquals(2, repository.findById(REQUEST_ID).orElseThrow().publicationAttemptCount());
  }

  @Test
  void anExpiredClaimCanBeRecoveredByAnotherWorkerWithANewFence() {
    repository.request(request(REQUEST_ID, record("{}")));
    repository.claimPublishable(
        new ConnectorDeadLetterReplayClaim(
            "worker-a", 1, NOW.plusSeconds(1), Duration.ofSeconds(30)));

    ClaimedConnectorDeadLetterReplay recovered =
        repository
            .claimPublishable(
                new ConnectorDeadLetterReplayClaim(
                    "worker-b", 1, NOW.plusSeconds(32), Duration.ofSeconds(30)))
            .getFirst();

    assertEquals("worker-b", recovered.claimOwner());
    assertEquals(2, recovered.publicationAttempt());
  }

  private static NewConnectorDeadLetterReplayRequest request(
      UUID requestId, ConnectorDeadLetterRecord record) {
    return new NewConnectorDeadLetterReplayRequest(
        requestId,
        record,
        record.fingerprint(),
        "https://issuer.example",
        "admin-42",
        "contract fixed",
        NOW);
  }

  private static ConnectorDeadLetterRecord record(String value) {
    return new ConnectorDeadLetterRecord(
        new ConnectorDeadLetterReference(2, 19),
        "connector.events.dlt",
        Optional.of("tenant:IMPORT_RUN:id"),
        Optional.of(value),
        "connector.events",
        2,
        11,
        NOW.minusSeconds(60),
        "invalid-connector-event-payload",
        false,
        "java.lang.IllegalArgumentException",
        Optional.of("invalid payload"),
        0,
        List.of(
            ConnectorDeadLetterHeader.fromBytes(
                "traceparent", "trace-1".getBytes(StandardCharsets.UTF_8))));
  }
}
