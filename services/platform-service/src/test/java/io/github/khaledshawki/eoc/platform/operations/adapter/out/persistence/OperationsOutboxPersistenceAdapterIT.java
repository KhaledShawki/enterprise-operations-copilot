package io.github.khaledshawki.eoc.platform.operations.adapter.out.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.khaledshawki.eoc.operations.application.exception.OperationsOutboxClaimLostException;
import io.github.khaledshawki.eoc.operations.application.model.event.InvoiceSynchronizedPayload;
import io.github.khaledshawki.eoc.operations.application.model.event.OperationsIntegrationEvent;
import io.github.khaledshawki.eoc.operations.application.model.event.OperationsIntegrationEventType;
import io.github.khaledshawki.eoc.operations.application.model.event.OperationsMoneyPayload;
import io.github.khaledshawki.eoc.operations.application.model.event.PaymentSynchronizedPayload;
import io.github.khaledshawki.eoc.operations.application.model.event.PendingOperationsIntegrationEvent;
import io.github.khaledshawki.eoc.operations.application.model.event.SourceRecordEvidence;
import io.github.khaledshawki.eoc.operations.application.model.outbox.ClaimedOperationsOutboxEvent;
import io.github.khaledshawki.eoc.operations.application.model.outbox.OperationsOutboxClaim;
import io.github.khaledshawki.eoc.operations.application.model.outbox.OperationsOutboxPublicationFailure;
import io.github.khaledshawki.eoc.operations.application.model.outbox.OperationsOutboxPublicationRetry;
import io.github.khaledshawki.eoc.operations.application.model.outbox.OperationsOutboxPublicationSuccess;
import io.github.khaledshawki.eoc.operations.application.port.out.OperationsIntegrationEventOutbox;
import io.github.khaledshawki.eoc.operations.application.port.out.OperationsOutboxRepository;
import io.github.khaledshawki.eoc.platform.TestcontainersConfiguration;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
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
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@Import({
  TestcontainersConfiguration.class,
  OperationsOutboxPersistenceAdapterIT.FixedClockConfiguration.class
})
class OperationsOutboxPersistenceAdapterIT {

  private static final Instant NOW = Instant.parse("2026-08-11T12:00:00Z");
  private static final Instant OCCURRED_AT = NOW.minusSeconds(10);
  private static final Duration CLAIM_LEASE = Duration.ofMinutes(1);
  private static final UUID TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000601");
  private static final UUID CUSTOMER_ID = UUID.fromString("00000000-0000-0000-0000-000000000602");
  private static final UUID INVOICE_ID = UUID.fromString("00000000-0000-0000-0000-000000000603");
  private static final UUID PAYMENT_ID = UUID.fromString("00000000-0000-0000-0000-000000000604");
  private static final UUID SOURCE_SYSTEM_ID =
      UUID.fromString("00000000-0000-0000-0000-000000000605");

  @Autowired private OperationsIntegrationEventOutbox eventOutbox;
  @Autowired private OperationsOutboxRepository outboxRepository;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private PlatformTransactionManager transactionManager;

  @BeforeEach
  void setUp() {
    jdbcTemplate.update("DELETE FROM operations_outbox_events");
    jdbcTemplate.update("DELETE FROM operations_event_stream_versions");
  }

  @Test
  void shouldRejectAppendWithoutAnOwningBusinessTransaction() {
    assertThrows(
        IllegalTransactionStateException.class,
        () -> eventOutbox.append(invoiceEvent(INVOICE_ID, "INV-601")));
    assertEquals(0L, count("operations_outbox_events"));
    assertEquals(0L, count("operations_event_stream_versions"));
  }

  @Test
  void shouldAllocateMonotonicVersionsAndPersistPortableJson() {
    OperationsIntegrationEvent first = append(invoiceEvent(INVOICE_ID, "INV-601"));
    OperationsIntegrationEvent second = append(invoiceEvent(INVOICE_ID, "INV-602"));

    assertEquals(1, first.aggregateVersion());
    assertEquals(2, second.aggregateVersion());
    assertNotEquals(first.eventId(), second.eventId());
    assertEquals(
        2L,
        jdbcTemplate.queryForObject(
            """
            SELECT last_version
            FROM operations_event_stream_versions
            WHERE tenant_id = ? AND aggregate_type = 'INVOICE' AND aggregate_id = ?
            """,
            Long.class,
            TENANT_ID,
            INVOICE_ID));
    assertEquals(
        INVOICE_ID.toString(),
        jdbcTemplate.queryForObject(
            """
            SELECT payload ->> 'invoiceId'
            FROM operations_outbox_events
            WHERE event_id = ?
            """,
            String.class,
            first.eventId()));
    assertEquals(
        "EUR",
        jdbcTemplate.queryForObject(
            """
            SELECT payload -> 'originalAmount' ->> 'currency'
            FROM operations_outbox_events
            WHERE event_id = ?
            """,
            String.class,
            first.eventId()));
  }

  @Test
  void shouldClaimOnlyEachAggregateHeadAndUnblockItsSuccessorAfterPublication() {
    OperationsIntegrationEvent invoiceFirst = append(invoiceEvent(INVOICE_ID, "INV-601"));
    OperationsIntegrationEvent invoiceSecond = append(invoiceEvent(INVOICE_ID, "INV-602"));
    OperationsIntegrationEvent payment = append(paymentEvent(PAYMENT_ID));

    List<ClaimedOperationsOutboxEvent> claimed =
        outboxRepository.claimPublishable(claim("worker-a", NOW, 10));

    assertEquals(2, claimed.size());
    assertTrue(claimed.stream().anyMatch(event -> event.eventId().equals(invoiceFirst.eventId())));
    assertTrue(claimed.stream().anyMatch(event -> event.eventId().equals(payment.eventId())));
    assertTrue(
        claimed.stream().noneMatch(event -> event.eventId().equals(invoiceSecond.eventId())));

    ClaimedOperationsOutboxEvent claimedInvoice =
        claimed.stream()
            .filter(event -> event.eventId().equals(invoiceFirst.eventId()))
            .findFirst()
            .orElseThrow();
    outboxRepository.markPublished(success(claimedInvoice, NOW.plusSeconds(1)));

    List<ClaimedOperationsOutboxEvent> next =
        outboxRepository.claimPublishable(claim("worker-b", NOW.plusSeconds(2), 10));
    assertEquals(
        List.of(invoiceSecond.eventId()),
        next.stream().map(ClaimedOperationsOutboxEvent::eventId).toList());
  }

  @Test
  void shouldBlockOneAggregateDuringRetryAndAfterTerminalFailureWithoutBlockingAnother() {
    OperationsIntegrationEvent invoiceFirst = append(invoiceEvent(INVOICE_ID, "INV-601"));
    OperationsIntegrationEvent invoiceSecond = append(invoiceEvent(INVOICE_ID, "INV-602"));
    OperationsIntegrationEvent paymentFirst = append(paymentEvent(PAYMENT_ID));
    List<ClaimedOperationsOutboxEvent> firstClaims =
        outboxRepository.claimPublishable(claim("worker-a", NOW, 10));
    ClaimedOperationsOutboxEvent claimedInvoice = find(firstClaims, invoiceFirst.eventId());
    ClaimedOperationsOutboxEvent claimedPayment = find(firstClaims, paymentFirst.eventId());

    outboxRepository.scheduleRetry(
        new OperationsOutboxPublicationRetry(
            claimedInvoice.eventId(),
            claimedInvoice.claimOwner(),
            claimedInvoice.publicationAttempt(),
            "broker-unavailable",
            NOW.plusSeconds(30),
            NOW.plusSeconds(1)));
    outboxRepository.markPublished(success(claimedPayment, NOW.plusSeconds(1)));
    OperationsIntegrationEvent paymentSecond = append(paymentEvent(PAYMENT_ID));

    List<ClaimedOperationsOutboxEvent> unrelatedProgress =
        outboxRepository.claimPublishable(claim("worker-b", NOW.plusSeconds(2), 10));
    assertEquals(
        List.of(paymentSecond.eventId()),
        unrelatedProgress.stream().map(ClaimedOperationsOutboxEvent::eventId).toList());
    assertTrue(
        unrelatedProgress.stream()
            .noneMatch(event -> event.eventId().equals(invoiceSecond.eventId())));

    ClaimedOperationsOutboxEvent retry =
        outboxRepository.claimPublishable(claim("worker-c", NOW.plusSeconds(30), 10)).stream()
            .filter(event -> event.eventId().equals(invoiceFirst.eventId()))
            .findFirst()
            .orElseThrow();
    outboxRepository.markFailed(
        new OperationsOutboxPublicationFailure(
            retry.eventId(),
            retry.claimOwner(),
            retry.publicationAttempt(),
            "contract-rejected",
            NOW.plusSeconds(31)));

    assertTrue(
        outboxRepository.claimPublishable(claim("worker-d", NOW.plusSeconds(60), 10)).isEmpty());
    assertEquals("PENDING", storedStatus(invoiceSecond.eventId()));
  }

  @Test
  void shouldReclaimExpiredClaimsAndFenceEveryStaleOutcome() {
    OperationsIntegrationEvent event = append(invoiceEvent(INVOICE_ID, "INV-601"));
    ClaimedOperationsOutboxEvent stale =
        outboxRepository.claimPublishable(claim("worker-a", NOW, 1)).getFirst();
    ClaimedOperationsOutboxEvent current =
        outboxRepository.claimPublishable(claim("worker-b", NOW.plus(CLAIM_LEASE), 1)).getFirst();

    assertEquals(event.eventId(), current.eventId());
    assertEquals(2, current.publicationAttempt());
    assertThrows(
        OperationsOutboxClaimLostException.class,
        () -> outboxRepository.markPublished(success(stale, NOW.plusSeconds(61))));
    assertThrows(
        OperationsOutboxClaimLostException.class,
        () ->
            outboxRepository.scheduleRetry(
                new OperationsOutboxPublicationRetry(
                    stale.eventId(),
                    stale.claimOwner(),
                    stale.publicationAttempt(),
                    "stale-retry",
                    NOW.plusSeconds(90),
                    NOW.plusSeconds(61))));
    assertThrows(
        OperationsOutboxClaimLostException.class,
        () ->
            outboxRepository.markFailed(
                new OperationsOutboxPublicationFailure(
                    stale.eventId(),
                    stale.claimOwner(),
                    stale.publicationAttempt(),
                    "stale-failure",
                    NOW.plusSeconds(61))));

    outboxRepository.markPublished(success(current, NOW.plusSeconds(62)));
    assertEquals("PUBLISHED", storedStatus(current.eventId()));
  }

  @Test
  void shouldSerializeConcurrentVersionAllocationPerAggregate() throws Exception {
    CountDownLatch ready = new CountDownLatch(2);
    CountDownLatch start = new CountDownLatch(1);

    try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
      List<Future<OperationsIntegrationEvent>> futures =
          List.of(
              executor.submit(() -> appendConcurrently("INV-601", ready, start)),
              executor.submit(() -> appendConcurrently("INV-602", ready, start)));
      assertTrue(ready.await(5, TimeUnit.SECONDS));
      start.countDown();

      List<OperationsIntegrationEvent> events = new ArrayList<>();
      for (Future<OperationsIntegrationEvent> future : futures) {
        events.add(future.get(5, TimeUnit.SECONDS));
      }
      events.sort(Comparator.comparingLong(OperationsIntegrationEvent::aggregateVersion));

      assertEquals(
          List.of(1L, 2L),
          events.stream().map(OperationsIntegrationEvent::aggregateVersion).toList());
      assertEquals(2L, count("operations_outbox_events"));
      assertEquals(1L, count("operations_event_stream_versions"));
    }
  }

  private OperationsIntegrationEvent appendConcurrently(
      String invoiceNumber, CountDownLatch ready, CountDownLatch start) throws Exception {
    ready.countDown();
    assertTrue(start.await(5, TimeUnit.SECONDS));
    return append(invoiceEvent(INVOICE_ID, invoiceNumber));
  }

  private OperationsIntegrationEvent append(PendingOperationsIntegrationEvent event) {
    OperationsIntegrationEvent appended =
        new TransactionTemplate(transactionManager).execute(status -> eventOutbox.append(event));
    return Objects.requireNonNull(appended, "Operations outbox transaction returned null");
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
            money("100.00"),
            money("0.00"),
            LocalDate.of(2026, 8, 1),
            LocalDate.of(2026, 8, 31),
            false,
            "OPEN",
            source()));
  }

  private static PendingOperationsIntegrationEvent paymentEvent(UUID paymentId) {
    return new PendingOperationsIntegrationEvent(
        OperationsIntegrationEventType.PAYMENT_SYNCHRONIZED,
        TENANT_ID,
        OCCURRED_AT,
        new PaymentSynchronizedPayload(
            paymentId,
            CUSTOMER_ID,
            money("50.00"),
            LocalDate.of(2026, 8, 1),
            false,
            "RECORDED",
            source()));
  }

  private static OperationsMoneyPayload money(String amount) {
    return new OperationsMoneyPayload(new BigDecimal(amount), "EUR");
  }

  private static SourceRecordEvidence source() {
    return new SourceRecordEvidence(
        SOURCE_SYSTEM_ID, "SOURCE_RECORD_ID", "source-601", "v1", Optional.empty());
  }

  private static OperationsOutboxClaim claim(String owner, Instant claimedAt, int batchSize) {
    return new OperationsOutboxClaim(owner, batchSize, claimedAt, CLAIM_LEASE);
  }

  private static OperationsOutboxPublicationSuccess success(
      ClaimedOperationsOutboxEvent event, Instant publishedAt) {
    return new OperationsOutboxPublicationSuccess(
        event.eventId(), event.claimOwner(), event.publicationAttempt(), publishedAt);
  }

  private static ClaimedOperationsOutboxEvent find(
      List<ClaimedOperationsOutboxEvent> events, UUID eventId) {
    return events.stream()
        .filter(event -> event.eventId().equals(eventId))
        .findFirst()
        .orElseThrow();
  }

  private String storedStatus(UUID eventId) {
    return jdbcTemplate.queryForObject(
        "SELECT publish_status FROM operations_outbox_events WHERE event_id = ?",
        String.class,
        eventId);
  }

  private long count(String table) {
    return jdbcTemplate.queryForObject("SELECT count(*) FROM " + table, Long.class);
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class FixedClockConfiguration {

    @Bean
    @Primary
    Clock fixedOperationsOutboxClock() {
      return Clock.fixed(NOW, ZoneOffset.UTC);
    }
  }
}
