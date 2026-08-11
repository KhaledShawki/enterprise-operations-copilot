package io.github.khaledshawki.eoc.platform.operations.adapter.out.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.khaledshawki.eoc.operations.application.exception.OperationsOutboxEventNotFoundException;
import io.github.khaledshawki.eoc.operations.application.exception.OperationsOutboxRecoveryConflictException;
import io.github.khaledshawki.eoc.operations.application.model.authorization.OperationsActor;
import io.github.khaledshawki.eoc.operations.application.model.event.InvoiceSynchronizedPayload;
import io.github.khaledshawki.eoc.operations.application.model.event.OperationsIntegrationEvent;
import io.github.khaledshawki.eoc.operations.application.model.event.OperationsIntegrationEventType;
import io.github.khaledshawki.eoc.operations.application.model.event.OperationsMoneyPayload;
import io.github.khaledshawki.eoc.operations.application.model.event.PendingOperationsIntegrationEvent;
import io.github.khaledshawki.eoc.operations.application.model.event.SourceRecordEvidence;
import io.github.khaledshawki.eoc.operations.application.model.outbox.ClaimedOperationsOutboxEvent;
import io.github.khaledshawki.eoc.operations.application.model.outbox.NewOperationsOutboxRecovery;
import io.github.khaledshawki.eoc.operations.application.model.outbox.OperationsOutboxClaim;
import io.github.khaledshawki.eoc.operations.application.model.outbox.OperationsOutboxInspectionFilter;
import io.github.khaledshawki.eoc.operations.application.model.outbox.OperationsOutboxPage;
import io.github.khaledshawki.eoc.operations.application.model.outbox.OperationsOutboxPublicationFailure;
import io.github.khaledshawki.eoc.operations.application.model.outbox.OperationsOutboxPublicationRetry;
import io.github.khaledshawki.eoc.operations.application.model.outbox.OperationsOutboxPublicationSuccess;
import io.github.khaledshawki.eoc.operations.application.model.outbox.OperationsOutboxRecovery;
import io.github.khaledshawki.eoc.operations.application.model.outbox.OperationsOutboxStatus;
import io.github.khaledshawki.eoc.operations.application.port.out.OperationsIntegrationEventOutbox;
import io.github.khaledshawki.eoc.operations.application.port.out.OperationsOutboxInspectionRepository;
import io.github.khaledshawki.eoc.operations.application.port.out.OperationsOutboxRecoveryRepository;
import io.github.khaledshawki.eoc.operations.application.port.out.OperationsOutboxRepository;
import io.github.khaledshawki.eoc.platform.TestcontainersConfiguration;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@Import({
  TestcontainersConfiguration.class,
  OperationsOutboxAdministrationPersistenceAdapterIT.FixedClockConfiguration.class
})
class OperationsOutboxAdministrationPersistenceAdapterIT {

  private static final Instant NOW = Instant.parse("2026-08-11T15:00:00Z");
  private static final Instant OCCURRED_AT = NOW.minusSeconds(10);
  private static final Duration CLAIM_LEASE = Duration.ofMinutes(1);
  private static final UUID TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000901");
  private static final UUID CUSTOMER_ID = UUID.fromString("00000000-0000-0000-0000-000000000902");
  private static final UUID INVOICE_ID = UUID.fromString("00000000-0000-0000-0000-000000000903");
  private static final UUID SECOND_INVOICE_ID =
      UUID.fromString("00000000-0000-0000-0000-000000000904");
  private static final UUID SOURCE_SYSTEM_ID =
      UUID.fromString("00000000-0000-0000-0000-000000000905");
  private static final OperationsActor ACTOR =
      new OperationsActor("http://localhost:8180/realms/eoc", "platform-admin-1");

  @Autowired private OperationsIntegrationEventOutbox eventOutbox;
  @Autowired private OperationsOutboxRepository outboxRepository;
  @Autowired private OperationsOutboxInspectionRepository inspectionRepository;
  @Autowired private OperationsOutboxRecoveryRepository recoveryRepository;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private PlatformTransactionManager transactionManager;

  @BeforeEach
  void setUp() {
    jdbcTemplate.update("DELETE FROM operations_outbox_recoveries");
    jdbcTemplate.update("DELETE FROM operations_outbox_events");
    jdbcTemplate.update("DELETE FROM operations_event_stream_versions");
  }

  @Test
  void shouldRecoverTheSameImmutableFailedEventWithAFreshGenerationAttemptBudget() {
    OperationsIntegrationEvent event = append(invoiceEvent(INVOICE_ID, "INV-901"));
    ClaimedOperationsOutboxEvent claimed = claim(event.eventId(), "worker-a", NOW);
    outboxRepository.markFailed(
        new OperationsOutboxPublicationFailure(
            claimed.eventId(),
            claimed.claimOwner(),
            claimed.publicationAttempt(),
            "broker-unavailable",
            NOW.plusSeconds(1)));
    Map<String, Object> identityBefore = identity(event.eventId());

    OperationsOutboxRecovery recovery =
        recoveryRepository.recover(
            recovery(event.eventId(), "broker configuration corrected", NOW.plusSeconds(2), 1));

    assertEquals(1, recovery.recoveryGeneration());
    assertEquals(1, recovery.previousPublicationAttemptCount());
    assertEquals(1, recovery.previousGenerationAttemptCount());
    assertEquals("broker-unavailable", recovery.previousFailureCode());
    assertEquals(identityBefore, identity(event.eventId()));
    assertEquals("RETRY_SCHEDULED", storedString(event.eventId(), "publish_status"));
    assertEquals(1, storedInt(event.eventId(), "publish_attempt_count"));
    assertEquals(1, storedInt(event.eventId(), "recovery_generation"));
    assertEquals(0, storedInt(event.eventId(), "generation_attempt_count"));

    ClaimedOperationsOutboxEvent recoveredClaim =
        claim(event.eventId(), "worker-b", NOW.plusSeconds(3));
    assertEquals(2, recoveredClaim.publicationAttempt());
    assertEquals(1, recoveredClaim.recoveryGeneration());
    assertEquals(1, recoveredClaim.generationAttempt());
  }

  @Test
  void shouldRejectMissingPendingAndEvenExpiredClaimedEvents() {
    UUID missing = UUID.fromString("00000000-0000-0000-0000-000000000999");
    assertThrows(
        OperationsOutboxEventNotFoundException.class,
        () -> recoveryRepository.recover(recovery(missing, "missing", NOW, 9)));

    OperationsIntegrationEvent pending = append(invoiceEvent(INVOICE_ID, "INV-901"));
    assertThrows(
        OperationsOutboxRecoveryConflictException.class,
        () -> recoveryRepository.recover(recovery(pending.eventId(), "pending", NOW, 10)));

    ClaimedOperationsOutboxEvent claimed = claim(pending.eventId(), "worker-a", NOW);
    assertEquals(
        OperationsOutboxStatus.CLAIMED,
        inspectionRepository.findById(claimed.eventId()).orElseThrow().status());
    assertThrows(
        OperationsOutboxRecoveryConflictException.class,
        () ->
            recoveryRepository.recover(
                recovery(
                    claimed.eventId(),
                    "claim is old but must be reclaimed by the relay",
                    NOW.plus(CLAIM_LEASE).plusSeconds(1),
                    11)));

    outboxRepository.scheduleRetry(
        new OperationsOutboxPublicationRetry(
            claimed.eventId(),
            claimed.claimOwner(),
            claimed.publicationAttempt(),
            "broker-unavailable",
            NOW.plusSeconds(30),
            NOW.plusSeconds(1)));
    assertEquals(
        OperationsOutboxStatus.RETRY_SCHEDULED,
        inspectionRepository.findById(claimed.eventId()).orElseThrow().status());
    assertThrows(
        OperationsOutboxRecoveryConflictException.class,
        () ->
            recoveryRepository.recover(
                recovery(claimed.eventId(), "retry already scheduled", NOW.plusSeconds(2), 12)));

    ClaimedOperationsOutboxEvent retried =
        claim(claimed.eventId(), "worker-b", NOW.plusSeconds(30));
    outboxRepository.markPublished(
        new OperationsOutboxPublicationSuccess(
            retried.eventId(),
            retried.claimOwner(),
            retried.publicationAttempt(),
            NOW.plusSeconds(31)));
    assertEquals(
        OperationsOutboxStatus.PUBLISHED,
        inspectionRepository.findById(claimed.eventId()).orElseThrow().status());
    assertThrows(
        OperationsOutboxRecoveryConflictException.class,
        () ->
            recoveryRepository.recover(
                recovery(claimed.eventId(), "published cannot recover", NOW.plusSeconds(32), 13)));
  }

  @Test
  void shouldSerializeConcurrentRecoveryRequestsAndCreateExactlyOneGeneration() throws Exception {
    OperationsIntegrationEvent event = append(invoiceEvent(INVOICE_ID, "INV-901"));
    ClaimedOperationsOutboxEvent claimed = claim(event.eventId(), "worker-a", NOW);
    outboxRepository.markFailed(
        new OperationsOutboxPublicationFailure(
            claimed.eventId(),
            claimed.claimOwner(),
            claimed.publicationAttempt(),
            "contract-rejected",
            NOW.plusSeconds(1)));

    CountDownLatch ready = new CountDownLatch(2);
    CountDownLatch start = new CountDownLatch(1);
    try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
      Future<Boolean> first = executor.submit(() -> tryRecover(event.eventId(), 21, ready, start));
      Future<Boolean> second = executor.submit(() -> tryRecover(event.eventId(), 22, ready, start));
      assertTrue(ready.await(5, TimeUnit.SECONDS));
      start.countDown();

      int successes = Boolean.TRUE.equals(first.get(5, TimeUnit.SECONDS)) ? 1 : 0;
      successes += Boolean.TRUE.equals(second.get(5, TimeUnit.SECONDS)) ? 1 : 0;
      assertEquals(1, successes);
    }

    assertEquals(
        1L,
        jdbcTemplate.queryForObject(
            "SELECT count(*) FROM operations_outbox_recoveries WHERE event_id = ?",
            Long.class,
            event.eventId()));
    assertEquals(1, storedInt(event.eventId(), "recovery_generation"));
    assertEquals("RETRY_SCHEDULED", storedString(event.eventId(), "publish_status"));
  }

  @Test
  void shouldKeepTheSuccessorBlockedUntilTheRecoveredHeadIsPublished() {
    OperationsIntegrationEvent first = append(invoiceEvent(INVOICE_ID, "INV-901"));
    OperationsIntegrationEvent second = append(invoiceEvent(INVOICE_ID, "INV-902"));
    ClaimedOperationsOutboxEvent firstClaim = claim(first.eventId(), "worker-a", NOW);
    outboxRepository.markFailed(
        new OperationsOutboxPublicationFailure(
            firstClaim.eventId(),
            firstClaim.claimOwner(),
            firstClaim.publicationAttempt(),
            "broker-unavailable",
            NOW.plusSeconds(1)));
    recoveryRepository.recover(
        recovery(first.eventId(), "broker restored", NOW.plusSeconds(2), 31));

    List<ClaimedOperationsOutboxEvent> claims =
        outboxRepository.claimPublishable(claim("worker-b", NOW.plusSeconds(3), 10));
    assertEquals(
        List.of(first.eventId()),
        claims.stream().map(ClaimedOperationsOutboxEvent::eventId).toList());
    assertFalse(
        claims.stream().anyMatch(candidate -> candidate.eventId().equals(second.eventId())));

    ClaimedOperationsOutboxEvent recoveredHead = claims.getFirst();
    outboxRepository.markPublished(
        new OperationsOutboxPublicationSuccess(
            recoveredHead.eventId(),
            recoveredHead.claimOwner(),
            recoveredHead.publicationAttempt(),
            NOW.plusSeconds(4)));

    assertEquals(
        List.of(second.eventId()),
        outboxRepository.claimPublishable(claim("worker-c", NOW.plusSeconds(5), 10)).stream()
            .map(ClaimedOperationsOutboxEvent::eventId)
            .toList());
  }

  @Test
  void shouldEnforceRecoveryGenerationCounterConstraintsInPostgreSql() {
    OperationsIntegrationEvent event = append(invoiceEvent(INVOICE_ID, "INV-901"));

    assertThrows(
        DataIntegrityViolationException.class,
        () ->
            jdbcTemplate.update(
                """
                UPDATE operations_outbox_events
                SET generation_attempt_count = publish_attempt_count + 1
                WHERE event_id = ?
                """,
                event.eventId()));
    assertEquals(0, storedInt(event.eventId(), "generation_attempt_count"));
  }

  @Test
  void shouldProvideBoundedFilteredCursorInspectionWithoutPayloadMaterial() {
    append(invoiceEvent(INVOICE_ID, "INV-901"));
    append(invoiceEvent(SECOND_INVOICE_ID, "INV-902"));

    OperationsOutboxInspectionFilter firstFilter =
        new OperationsOutboxInspectionFilter(
            Optional.of(OperationsOutboxStatus.PENDING),
            Optional.of(TENANT_ID),
            Optional.of("invoice"),
            Optional.empty(),
            Optional.empty(),
            1);
    OperationsOutboxPage firstPage = inspectionRepository.list(firstFilter);
    assertEquals(1, firstPage.events().size());
    assertTrue(firstPage.nextCursor().isPresent());

    OperationsOutboxPage secondPage =
        inspectionRepository.list(
            new OperationsOutboxInspectionFilter(
                firstFilter.status(),
                firstFilter.tenantId(),
                firstFilter.aggregateType(),
                firstFilter.aggregateId(),
                firstPage.nextCursor(),
                1));
    assertEquals(1, secondPage.events().size());
    assertTrue(secondPage.nextCursor().isEmpty());
    assertFalse(
        firstPage.events().getFirst().eventId().equals(secondPage.events().getFirst().eventId()));
  }

  private boolean tryRecover(UUID eventId, int idSuffix, CountDownLatch ready, CountDownLatch start)
      throws Exception {
    ready.countDown();
    assertTrue(start.await(5, TimeUnit.SECONDS));
    try {
      recoveryRepository.recover(
          recovery(eventId, "concurrent recovery", NOW.plusSeconds(2), idSuffix));
      return true;
    } catch (OperationsOutboxRecoveryConflictException exception) {
      return false;
    }
  }

  private OperationsIntegrationEvent append(PendingOperationsIntegrationEvent event) {
    OperationsIntegrationEvent appended =
        new TransactionTemplate(transactionManager).execute(status -> eventOutbox.append(event));
    if (appended == null) {
      throw new IllegalStateException("Operations outbox transaction returned null");
    }
    return appended;
  }

  private ClaimedOperationsOutboxEvent claim(UUID eventId, String worker, Instant at) {
    return outboxRepository.claimPublishable(claim(worker, at, 10)).stream()
        .filter(event -> event.eventId().equals(eventId))
        .findFirst()
        .orElseThrow();
  }

  private static OperationsOutboxClaim claim(String worker, Instant at, int batchSize) {
    return new OperationsOutboxClaim(worker, batchSize, at, CLAIM_LEASE);
  }

  private static NewOperationsOutboxRecovery recovery(
      UUID eventId, String reason, Instant requestedAt, int idSuffix) {
    return new NewOperationsOutboxRecovery(
        UUID.fromString(String.format("00000000-0000-0000-0000-%012d", idSuffix)),
        ACTOR,
        eventId,
        reason,
        requestedAt);
  }

  private Map<String, Object> identity(UUID eventId) {
    return jdbcTemplate.queryForMap(
        """
        SELECT
          event_id,
          event_type,
          schema_version,
          tenant_id,
          aggregate_type,
          aggregate_id,
          aggregate_version,
          payload::text AS payload,
          occurred_at
        FROM operations_outbox_events
        WHERE event_id = ?
        """,
        eventId);
  }

  private String storedString(UUID eventId, String column) {
    return jdbcTemplate.queryForObject(
        "SELECT " + column + " FROM operations_outbox_events WHERE event_id = ?",
        String.class,
        eventId);
  }

  private int storedInt(UUID eventId, String column) {
    Integer value =
        jdbcTemplate.queryForObject(
            "SELECT " + column + " FROM operations_outbox_events WHERE event_id = ?",
            Integer.class,
            eventId);
    return value == null ? -1 : value;
  }

  private static PendingOperationsIntegrationEvent invoiceEvent(
      UUID invoiceId, String invoiceNumber) {
    return new PendingOperationsIntegrationEvent(
        OperationsIntegrationEventType.INVOICE_SYNCHRONIZED,
        TENANT_ID,
        OCCURRED_AT,
        new InvoiceSynchronizedPayload(
            invoiceId,
            CUSTOMER_ID,
            invoiceNumber,
            new OperationsMoneyPayload(new BigDecimal("100.00"), "EUR"),
            new OperationsMoneyPayload(new BigDecimal("0.00"), "EUR"),
            LocalDate.of(2026, 8, 1),
            LocalDate.of(2026, 8, 31),
            false,
            "OPEN",
            new SourceRecordEvidence(
                SOURCE_SYSTEM_ID,
                "SOURCE_RECORD_ID",
                "source-" + invoiceNumber,
                "v1",
                Optional.empty())));
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class FixedClockConfiguration {

    @Bean
    @Primary
    Clock fixedOperationsOutboxAdministrationClock() {
      return Clock.fixed(NOW, ZoneOffset.UTC);
    }
  }
}
