package io.github.khaledshawki.eoc.platform.connectormanagement.adapter.out.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.khaledshawki.eoc.connectormanagement.application.exception.ConnectorOutboxClaimLostException;
import io.github.khaledshawki.eoc.connectormanagement.application.model.event.ImportRunIntegrationEventFactory;
import io.github.khaledshawki.eoc.connectormanagement.application.model.outbox.ClaimedConnectorOutboxEvent;
import io.github.khaledshawki.eoc.connectormanagement.application.model.outbox.ConnectorOutboxClaim;
import io.github.khaledshawki.eoc.connectormanagement.application.model.outbox.ConnectorOutboxPublicationFailure;
import io.github.khaledshawki.eoc.connectormanagement.application.model.outbox.ConnectorOutboxPublicationRetry;
import io.github.khaledshawki.eoc.connectormanagement.application.model.outbox.ConnectorOutboxPublicationSuccess;
import io.github.khaledshawki.eoc.connectormanagement.application.port.out.ConnectorOutboxRepository;
import io.github.khaledshawki.eoc.connectormanagement.application.port.out.ConnectorRepository;
import io.github.khaledshawki.eoc.connectormanagement.application.port.out.ImportRunRepository;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.Connector;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ConnectorEndpoint;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ConnectorName;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ConnectorTenantId;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ConnectorType;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.CredentialReference;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ImportMode;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ImportRun;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ImportType;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.SyncPolicy;
import io.github.khaledshawki.eoc.platform.TestcontainersConfiguration;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
@Import({
  TestcontainersConfiguration.class,
  ConnectorOutboxPersistenceAdapterIT.FixedClockConfiguration.class
})
class ConnectorOutboxPersistenceAdapterIT {

  private static final Instant NOW = Instant.parse("2026-08-03T18:00:00Z");
  private static final Duration CLAIM_LEASE = Duration.ofMinutes(1);
  private static final ConnectorTenantId TENANT_ID =
      ConnectorTenantId.of(UUID.fromString("00000000-0000-0000-0000-000000000010"));

  @Autowired private ConnectorOutboxRepository outboxRepository;
  @Autowired private ConnectorRepository connectorRepository;
  @Autowired private ImportRunRepository importRunRepository;
  @Autowired private SpringDataConnectorRepository springDataConnectorRepository;
  @Autowired private SpringDataImportRunRepository springDataImportRunRepository;
  @Autowired private SpringDataImportCheckpointRepository checkpointRepository;
  @Autowired private SpringDataImportPageAcceptanceRepository acceptanceRepository;
  @Autowired private JdbcTemplate jdbcTemplate;

  private Connector connector;

  @BeforeEach
  void setUp() {
    jdbcTemplate.update("DELETE FROM connector_outbox_events");
    acceptanceRepository.deleteAllInBatch();
    checkpointRepository.deleteAllInBatch();
    springDataImportRunRepository.deleteAllInBatch();
    springDataConnectorRepository.deleteAllInBatch();
    connector = connectorRepository.save(activeConnector());
  }

  @Test
  void shouldClaimPublishableEventsInDeterministicOrder() {
    createEvent(eventId(2));
    createEvent(eventId(1));

    List<ClaimedConnectorOutboxEvent> claimed =
        outboxRepository.claimPublishable(claim("worker-a", NOW, 10));

    assertEquals(List.of(eventId(1), eventId(2)), eventIds(claimed));
    assertEquals(
        List.of(1, 1),
        claimed.stream().map(ClaimedConnectorOutboxEvent::publicationAttempt).toList());
    assertTrue(claimed.stream().allMatch(event -> event.claimOwner().equals("worker-a")));
  }

  @Test
  void shouldAllowOnlyOneConcurrentWorkerToClaimTheSameEvent() throws Exception {
    createEvent(eventId(1));
    CountDownLatch ready = new CountDownLatch(2);
    CountDownLatch start = new CountDownLatch(1);

    try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
      Future<List<ClaimedConnectorOutboxEvent>> first =
          executor.submit(() -> concurrentClaim("worker-a", ready, start));
      Future<List<ClaimedConnectorOutboxEvent>> second =
          executor.submit(() -> concurrentClaim("worker-b", ready, start));

      assertTrue(ready.await(5, TimeUnit.SECONDS));
      start.countDown();
      List<ClaimedConnectorOutboxEvent> all = new ArrayList<>();
      all.addAll(first.get(5, TimeUnit.SECONDS));
      all.addAll(second.get(5, TimeUnit.SECONDS));

      assertEquals(1, all.size());
      assertEquals(eventId(1), all.getFirst().eventId());
      assertEquals(1, all.getFirst().publicationAttempt());
    }
  }

  @Test
  void shouldReclaimACrashedWorkerEventOnlyAfterTheLeaseExpires() {
    createEvent(eventId(1));
    ClaimedConnectorOutboxEvent first =
        outboxRepository.claimPublishable(claim("worker-a", NOW, 1)).getFirst();

    assertTrue(
        outboxRepository.claimPublishable(claim("worker-b", NOW.plusSeconds(59), 1)).isEmpty());

    ClaimedConnectorOutboxEvent replay =
        outboxRepository.claimPublishable(claim("worker-b", NOW.plusSeconds(60), 1)).getFirst();

    assertEquals(first.eventId(), replay.eventId());
    assertEquals(2, replay.publicationAttempt());
    assertEquals("worker-b", replay.claimOwner());
  }

  @Test
  void shouldPublishOnlyThroughTheCurrentClaimOwner() {
    createEvent(eventId(1));
    ClaimedConnectorOutboxEvent claimed =
        outboxRepository.claimPublishable(claim("worker-a", NOW, 1)).getFirst();

    assertThrows(
        ConnectorOutboxClaimLostException.class,
        () ->
            outboxRepository.markPublished(
                new ConnectorOutboxPublicationSuccess(
                    claimed.eventId(),
                    "worker-b",
                    claimed.publicationAttempt(),
                    NOW.plusSeconds(1))));

    outboxRepository.markPublished(
        new ConnectorOutboxPublicationSuccess(
            claimed.eventId(),
            claimed.claimOwner(),
            claimed.publicationAttempt(),
            NOW.plusSeconds(1)));

    assertEquals("PUBLISHED", storedString(claimed.eventId(), "publish_status"));
    assertEquals(1, storedInteger(claimed.eventId(), "publish_attempt_count"));
    assertEquals(0, countPublishable(NOW.plusSeconds(60)));
  }

  @Test
  void shouldFenceAStaleClaimEvenWhenTheWorkerIdentifierIsReused() {
    createEvent(eventId(1));
    ClaimedConnectorOutboxEvent stale =
        outboxRepository.claimPublishable(claim("worker-a", NOW, 1)).getFirst();
    ClaimedConnectorOutboxEvent current =
        outboxRepository.claimPublishable(claim("worker-a", NOW.plus(CLAIM_LEASE), 1)).getFirst();

    assertEquals(2, current.publicationAttempt());
    assertThrows(
        ConnectorOutboxClaimLostException.class,
        () ->
            outboxRepository.markPublished(
                new ConnectorOutboxPublicationSuccess(
                    stale.eventId(),
                    stale.claimOwner(),
                    stale.publicationAttempt(),
                    NOW.plusSeconds(61))));
    assertThrows(
        ConnectorOutboxClaimLostException.class,
        () ->
            outboxRepository.scheduleRetry(
                new ConnectorOutboxPublicationRetry(
                    stale.eventId(),
                    stale.claimOwner(),
                    stale.publicationAttempt(),
                    "stale-retry",
                    NOW.plusSeconds(90),
                    NOW.plusSeconds(61))));
    assertThrows(
        ConnectorOutboxClaimLostException.class,
        () ->
            outboxRepository.markFailed(
                new ConnectorOutboxPublicationFailure(
                    stale.eventId(),
                    stale.claimOwner(),
                    stale.publicationAttempt(),
                    "stale-failure",
                    NOW.plusSeconds(61))));

    outboxRepository.markPublished(
        new ConnectorOutboxPublicationSuccess(
            current.eventId(),
            current.claimOwner(),
            current.publicationAttempt(),
            NOW.plusSeconds(62)));
    assertEquals("PUBLISHED", storedString(current.eventId(), "publish_status"));
  }

  @Test
  void shouldScheduleRetryAndClaimItAgainOnlyWhenDue() {
    createEvent(eventId(1));
    ClaimedConnectorOutboxEvent first =
        outboxRepository.claimPublishable(claim("worker-a", NOW, 1)).getFirst();
    Instant nextPublishAt = NOW.plusSeconds(30);

    outboxRepository.scheduleRetry(
        new ConnectorOutboxPublicationRetry(
            first.eventId(),
            first.claimOwner(),
            first.publicationAttempt(),
            "broker-unavailable",
            nextPublishAt,
            NOW.plusSeconds(1)));

    assertTrue(
        outboxRepository
            .claimPublishable(claim("worker-b", nextPublishAt.minusSeconds(1), 1))
            .isEmpty());
    ClaimedConnectorOutboxEvent retry =
        outboxRepository.claimPublishable(claim("worker-b", nextPublishAt, 1)).getFirst();

    assertEquals(2, retry.publicationAttempt());
    assertEquals("worker-b", retry.claimOwner());
    assertEquals("broker-unavailable", storedString(retry.eventId(), "last_failure_code"));
  }

  @Test
  void shouldMakeTerminalPublicationFailuresIneligibleForFurtherClaims() {
    createEvent(eventId(1));
    ClaimedConnectorOutboxEvent claimed =
        outboxRepository.claimPublishable(claim("worker-a", NOW, 1)).getFirst();

    outboxRepository.markFailed(
        new ConnectorOutboxPublicationFailure(
            claimed.eventId(),
            claimed.claimOwner(),
            claimed.publicationAttempt(),
            "contract-rejected",
            NOW.plusSeconds(1)));

    assertEquals("FAILED", storedString(claimed.eventId(), "publish_status"));
    assertEquals("contract-rejected", storedString(claimed.eventId(), "last_failure_code"));
    assertTrue(
        outboxRepository.claimPublishable(claim("worker-b", NOW.plusSeconds(120), 1)).isEmpty());
  }

  private List<ClaimedConnectorOutboxEvent> concurrentClaim(
      String workerId, CountDownLatch ready, CountDownLatch start) throws InterruptedException {
    ready.countDown();
    assertTrue(start.await(5, TimeUnit.SECONDS));
    return outboxRepository.claimPublishable(claim(workerId, NOW, 1));
  }

  private void createEvent(UUID eventId) {
    ImportRun run =
        importRunRepository.save(
            ImportRun.request(
                TENANT_ID,
                connector.id(),
                ImportType.CUSTOMERS,
                ImportMode.FULL,
                Optional.empty(),
                NOW));
    run.start(NOW);
    run = importRunRepository.save(run);
    run.complete(NOW);
    importRunRepository.saveWithEvent(
        run, ImportRunIntegrationEventFactory.completed(eventId, run, NOW));
  }

  private static ConnectorOutboxClaim claim(String owner, Instant claimedAt, int batchSize) {
    return new ConnectorOutboxClaim(owner, batchSize, claimedAt, CLAIM_LEASE);
  }

  private static List<UUID> eventIds(List<ClaimedConnectorOutboxEvent> events) {
    return events.stream().map(ClaimedConnectorOutboxEvent::eventId).toList();
  }

  private static UUID eventId(long sequence) {
    return new UUID(0L, sequence);
  }

  private String storedString(UUID eventId, String column) {
    return jdbcTemplate.queryForObject(
        "SELECT " + column + " FROM connector_outbox_events WHERE event_id = ?",
        String.class,
        eventId);
  }

  private int storedInteger(UUID eventId, String column) {
    return jdbcTemplate.queryForObject(
        "SELECT " + column + " FROM connector_outbox_events WHERE event_id = ?",
        Integer.class,
        eventId);
  }

  private int countPublishable(Instant now) {
    return jdbcTemplate.queryForObject(
        """
        SELECT count(*)
        FROM connector_outbox_events
        WHERE publish_status IN ('PENDING', 'RETRY_SCHEDULED')
          AND next_publish_at <= ?
        """,
        Integer.class,
        Timestamp.from(now));
  }

  private static Connector activeConnector() {
    Connector value =
        Connector.create(
            TENANT_ID,
            ConnectorName.of("Primary ERP"),
            ConnectorType.of("mock-erp"),
            ConnectorEndpoint.of("https://erp.example.com/api"),
            CredentialReference.of(UUID.randomUUID()),
            SyncPolicy.manual());
    value.activate();
    return value;
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class FixedClockConfiguration {

    @Bean
    @Primary
    Clock connectorOutboxClock() {
      return Clock.fixed(NOW, ZoneOffset.UTC);
    }
  }
}
