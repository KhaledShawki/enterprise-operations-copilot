package io.github.khaledshawki.eoc.platform.analytics.adapter.out.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.khaledshawki.eoc.analytics.application.exception.AnalyticsEventConsumptionException;
import io.github.khaledshawki.eoc.analytics.application.exception.AnalyticsProjectionVersionGapException;
import io.github.khaledshawki.eoc.analytics.application.model.event.AnalyticsEventConsumptionStatus;
import io.github.khaledshawki.eoc.analytics.application.model.event.AnalyticsIntegrationEvent;
import io.github.khaledshawki.eoc.analytics.application.model.event.AnalyticsProjectionPayload;
import io.github.khaledshawki.eoc.analytics.application.port.in.ConsumeAnalyticsIntegrationEventUseCase;
import io.github.khaledshawki.eoc.platform.TestcontainersConfiguration;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(
    properties = {
      "eoc.connector-outbox.relay-enabled=false",
      "eoc.operations-outbox.relay-enabled=false",
      "eoc.analytics-events.transport=disabled"
    })
@Import(TestcontainersConfiguration.class)
class AnalyticsInboxPersistenceAdapterIT {

  private static final UUID TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000010");
  private static final UUID PARTNER_ID = UUID.fromString("00000000-0000-0000-0000-000000000282");
  private static final UUID INVOICE_ID = UUID.fromString("00000000-0000-0000-0000-000000000283");
  private static final UUID CUSTOMER_ID = UUID.fromString("00000000-0000-0000-0000-000000000284");
  private static final Instant OCCURRED_AT = Instant.parse("2026-08-11T21:00:00Z");

  @Autowired private ConsumeAnalyticsIntegrationEventUseCase consumeUseCase;
  @Autowired private AnalyticsInboxPersistenceAdapter inboxAdapter;
  @Autowired private JdbcTemplate jdbcTemplate;

  @BeforeEach
  void clearAnalyticsState() {
    jdbcTemplate.update("DELETE FROM analytics_business_partner_projections");
    jdbcTemplate.update("DELETE FROM analytics_invoice_receivable_projections");
    jdbcTemplate.update("DELETE FROM analytics_inbox_events");
  }

  @Test
  void rejectsStandaloneInboxWriteOutsideTheAtomicConsumptionTransaction() {
    AnalyticsIntegrationEvent event = businessPartnerEvent(UUID.randomUUID(), 1, "Acme AG");

    AnalyticsEventConsumptionException exception =
        assertThrows(AnalyticsEventConsumptionException.class, () -> inboxAdapter.accept(event));

    assertEquals("analytics-consumption-transaction-required", exception.failureCode());
    assertEquals(0, count("analytics_inbox_events"));
  }

  @Test
  void rejectsDistinctEventIdentityForAnAlreadyClaimedAggregateVersion() {
    consumeUseCase.consume(businessPartnerEvent(UUID.randomUUID(), 1, "Acme AG"));
    AnalyticsIntegrationEvent conflictingIdentity =
        businessPartnerEvent(UUID.randomUUID(), 1, "Acme AG");

    AnalyticsEventConsumptionException exception =
        assertThrows(
            AnalyticsEventConsumptionException.class,
            () -> consumeUseCase.consume(conflictingIdentity));

    assertEquals("analytics-inbox-contract-rejected", exception.failureCode());
    assertEquals(1, count("analytics_inbox_events"));
    assertEquals(1, count("analytics_business_partner_projections"));
  }

  @Test
  void commitsInboxAndBusinessPartnerProjectionTogether() {
    AnalyticsIntegrationEvent event = businessPartnerEvent(UUID.randomUUID(), 1, "Acme AG");

    var result = consumeUseCase.consume(event);

    assertEquals(AnalyticsEventConsumptionStatus.APPLIED, result.status());
    assertEquals(1, count("analytics_inbox_events"));
    assertEquals(1, count("analytics_business_partner_projections"));
    assertEquals(
        1L,
        jdbcTemplate.queryForObject(
            """
            SELECT aggregate_version
            FROM analytics_business_partner_projections
            WHERE tenant_id = ? AND business_partner_id = ?
            """,
            Long.class,
            TENANT_ID,
            PARTNER_ID));
    assertEquals(
        "APPLIED",
        jdbcTemplate.queryForObject(
            "SELECT projection_status FROM analytics_inbox_events WHERE event_id = ?",
            String.class,
            event.eventId()));
  }

  @Test
  void advancesInvoiceProjectionWithExpectedAggregateVersion() {
    consumeUseCase.consume(invoiceEvent(UUID.randomUUID(), 1, "0.00", "OPEN"));
    AnalyticsIntegrationEvent second =
        invoiceEvent(UUID.randomUUID(), 2, "20.00", "PARTIALLY_PAID");

    var result = consumeUseCase.consume(second);

    assertEquals(AnalyticsEventConsumptionStatus.APPLIED, result.status());
    assertEquals(2, count("analytics_inbox_events"));
    assertEquals(
        2L,
        jdbcTemplate.queryForObject(
            """
            SELECT aggregate_version
            FROM analytics_invoice_receivable_projections
            WHERE tenant_id = ? AND invoice_id = ?
            """,
            Long.class,
            TENANT_ID,
            INVOICE_ID));
    BigDecimal storedPaidAmount =
        jdbcTemplate.queryForObject(
            """
            SELECT paid_amount
            FROM analytics_invoice_receivable_projections
            WHERE tenant_id = ? AND invoice_id = ?
            """,
            BigDecimal.class,
            TENANT_ID,
            INVOICE_ID);
    assertEquals(0, storedPaidAmount.compareTo(new BigDecimal("20.00")));
  }

  @Test
  void absorbsIdenticalHistoricalReplayBeforeProjection() {
    AnalyticsIntegrationEvent event = businessPartnerEvent(UUID.randomUUID(), 1, "Acme AG");
    consumeUseCase.consume(event);

    var replay = consumeUseCase.consume(event);

    assertEquals(AnalyticsEventConsumptionStatus.DUPLICATE, replay.status());
    assertEquals(1, count("analytics_inbox_events"));
    assertEquals(1, count("analytics_business_partner_projections"));
  }

  @Test
  void rejectsEventIdCollisionWithoutChangingProjection() {
    UUID eventId = UUID.randomUUID();
    consumeUseCase.consume(businessPartnerEvent(eventId, 1, "Acme AG"));
    AnalyticsIntegrationEvent collision = businessPartnerEvent(eventId, 1, "Changed AG");

    AnalyticsEventConsumptionException exception =
        assertThrows(
            AnalyticsEventConsumptionException.class, () -> consumeUseCase.consume(collision));

    assertEquals("analytics-event-id-collision", exception.failureCode());
    assertEquals(
        "Acme AG",
        jdbcTemplate.queryForObject(
            """
            SELECT display_name
            FROM analytics_business_partner_projections
            WHERE tenant_id = ? AND business_partner_id = ?
            """,
            String.class,
            TENANT_ID,
            PARTNER_ID));
    assertEquals(1, count("analytics_inbox_events"));
  }

  @Test
  void rollsBackNewInboxRowWhenProjectionHasVersionGap() {
    consumeUseCase.consume(invoiceEvent(UUID.randomUUID(), 1, "0.00", "OPEN"));
    AnalyticsIntegrationEvent gap = invoiceEvent(UUID.randomUUID(), 3, "20.00", "PARTIALLY_PAID");

    assertThrows(AnalyticsProjectionVersionGapException.class, () -> consumeUseCase.consume(gap));

    assertEquals(1, count("analytics_inbox_events"));
    assertEquals(
        0,
        jdbcTemplate.queryForObject(
            "SELECT count(*) FROM analytics_inbox_events WHERE event_id = ?",
            Integer.class,
            gap.eventId()));
    assertEquals(
        1L,
        jdbcTemplate.queryForObject(
            """
            SELECT aggregate_version
            FROM analytics_invoice_receivable_projections
            WHERE tenant_id = ? AND invoice_id = ?
            """,
            Long.class,
            TENANT_ID,
            INVOICE_ID));
  }

  @Test
  void durablyAcceptsKnownPaymentWithoutCreatingProjection() {
    UUID paymentId = UUID.randomUUID();
    AnalyticsIntegrationEvent event =
        new AnalyticsIntegrationEvent(
            UUID.randomUUID(),
            "operations.payment.synchronized.v1",
            1,
            TENANT_ID,
            "PAYMENT",
            paymentId,
            1,
            """
            {"paymentId":"%s"}
            """
                .formatted(paymentId),
            OCCURRED_AT,
            new AnalyticsProjectionPayload.Ignored());

    var result = consumeUseCase.consume(event);

    assertEquals(AnalyticsEventConsumptionStatus.IGNORED, result.status());
    assertEquals(
        "IGNORED",
        jdbcTemplate.queryForObject(
            "SELECT projection_status FROM analytics_inbox_events WHERE event_id = ?",
            String.class,
            event.eventId()));
    assertEquals(0, count("analytics_business_partner_projections"));
    assertEquals(0, count("analytics_invoice_receivable_projections"));
  }

  private int count(String table) {
    return jdbcTemplate.queryForObject("SELECT count(*) FROM " + table, Integer.class);
  }

  private static AnalyticsIntegrationEvent businessPartnerEvent(
      UUID eventId, long version, String displayName) {
    return new AnalyticsIntegrationEvent(
        eventId,
        "operations.business-partner.synchronized.v1",
        1,
        TENANT_ID,
        "BUSINESS_PARTNER",
        PARTNER_ID,
        version,
        """
        {"businessPartnerId":"%s","displayName":"%s"}
        """
            .formatted(PARTNER_ID, displayName),
        OCCURRED_AT.plusSeconds(version),
        new AnalyticsProjectionPayload.BusinessPartner(
            PARTNER_ID, "C-100", displayName, Set.of("CUSTOMER")));
  }

  private static AnalyticsIntegrationEvent invoiceEvent(
      UUID eventId, long version, String paidAmount, String status) {
    return new AnalyticsIntegrationEvent(
        eventId,
        "operations.invoice.synchronized.v1",
        1,
        TENANT_ID,
        "INVOICE",
        INVOICE_ID,
        version,
        """
        {"invoiceId":"%s","paidAmount":%s}
        """
            .formatted(INVOICE_ID, paidAmount),
        OCCURRED_AT.plusSeconds(version),
        new AnalyticsProjectionPayload.InvoiceReceivable(
            INVOICE_ID,
            CUSTOMER_ID,
            "INV-100",
            new BigDecimal("100.00"),
            new BigDecimal(paidAmount),
            "CHF",
            LocalDate.parse("2026-07-01"),
            LocalDate.parse("2026-07-31"),
            false,
            status));
  }
}
