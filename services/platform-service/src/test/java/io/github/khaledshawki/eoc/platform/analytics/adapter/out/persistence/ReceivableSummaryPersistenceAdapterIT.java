package io.github.khaledshawki.eoc.platform.analytics.adapter.out.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.khaledshawki.eoc.analytics.application.model.event.AnalyticsIntegrationEvent;
import io.github.khaledshawki.eoc.analytics.application.model.event.AnalyticsProjectionPayload;
import io.github.khaledshawki.eoc.analytics.application.port.in.ConsumeAnalyticsIntegrationEventUseCase;
import io.github.khaledshawki.eoc.analytics.application.port.in.GetReceivablesSummaryQuery;
import io.github.khaledshawki.eoc.analytics.application.port.in.GetReceivablesSummaryUseCase;
import io.github.khaledshawki.eoc.platform.TestcontainersConfiguration;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
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
class ReceivableSummaryPersistenceAdapterIT {

  private static final UUID TENANT_A = UUID.fromString("00000000-0000-0000-0000-000000000501");
  private static final UUID TENANT_B = UUID.fromString("00000000-0000-0000-0000-000000000502");
  private static final UUID CUSTOMER_ID = UUID.fromString("00000000-0000-0000-0000-000000000503");
  private static final LocalDate BUSINESS_DATE = LocalDate.of(2026, 8, 12);
  private static final Instant OCCURRED_AT = Instant.parse("2026-08-10T12:00:00Z");

  @Autowired private ConsumeAnalyticsIntegrationEventUseCase consumeUseCase;
  @Autowired private GetReceivablesSummaryUseCase summaryUseCase;
  @Autowired private JdbcTemplate jdbcTemplate;

  @BeforeEach
  void setUp() {
    jdbcTemplate.update("DELETE FROM analytics_business_partner_projections");
    jdbcTemplate.update("DELETE FROM analytics_invoice_receivable_projections");
    jdbcTemplate.update("DELETE FROM analytics_inbox_events");

    consume(
        TENANT_A,
        "00000000-0000-0000-0000-000000000511",
        "EUR",
        "100.00",
        "0.00",
        BUSINESS_DATE,
        false,
        "OPEN");
    consume(
        TENANT_A,
        "00000000-0000-0000-0000-000000000512",
        "EUR",
        "100.00",
        "10.00",
        LocalDate.of(2026, 8, 1),
        false,
        "PARTIALLY_PAID");
    consume(
        TENANT_A,
        "00000000-0000-0000-0000-000000000513",
        "EUR",
        "80.00",
        "0.00",
        LocalDate.of(2026, 7, 1),
        false,
        "OPEN");
    consume(
        TENANT_A,
        "00000000-0000-0000-0000-000000000514",
        "EUR",
        "70.00",
        "0.00",
        LocalDate.of(2026, 6, 1),
        false,
        "OPEN");
    consume(
        TENANT_A,
        "00000000-0000-0000-0000-000000000515",
        "EUR",
        "60.00",
        "0.00",
        LocalDate.of(2026, 5, 1),
        false,
        "OPEN");
    consume(
        TENANT_A,
        "00000000-0000-0000-0000-000000000516",
        "EUR",
        "50.00",
        "50.00",
        LocalDate.of(2026, 7, 20),
        false,
        "PAID");
    consume(
        TENANT_A,
        "00000000-0000-0000-0000-000000000517",
        "EUR",
        "40.00",
        "0.00",
        LocalDate.of(2026, 7, 20),
        true,
        "CANCELLED");
    consume(
        TENANT_A,
        "00000000-0000-0000-0000-000000000518",
        "CHF",
        "200.00",
        "0.00",
        LocalDate.of(2026, 8, 10),
        false,
        "OPEN");
    consume(
        TENANT_A,
        "00000000-0000-0000-0000-000000000519",
        "CHF",
        "300.00",
        "0.00",
        LocalDate.of(2026, 8, 20),
        false,
        "OPEN");
    consume(
        TENANT_B,
        "00000000-0000-0000-0000-000000000520",
        "EUR",
        "999.00",
        "0.00",
        LocalDate.of(2026, 4, 1),
        false,
        "OPEN");
  }

  @Test
  void summarizesTenantReceivablesByCurrencyAndAgingBucket() {
    var result = summaryUseCase.get(new GetReceivablesSummaryQuery(TENANT_A, BUSINESS_DATE));

    assertEquals(9, result.invoiceCount());
    assertEquals(7, result.openCount());
    assertEquals(5, result.overdueCount());
    assertEquals(2, result.currencies().size());
    assertEquals("CHF", result.currencies().get(0).currency().value());
    assertEquals(new BigDecimal("500.00"), result.currencies().get(0).outstandingAmount().amount());
    assertEquals(new BigDecimal("200.00"), result.currencies().get(0).overdueAmount().amount());
    assertEquals(new BigDecimal("300.00"), result.currencies().get(0).currentAmount().amount());

    var eur = result.currencies().get(1);
    assertEquals("EUR", eur.currency().value());
    assertEquals(7, eur.invoiceCount());
    assertEquals(5, eur.openCount());
    assertEquals(4, eur.overdueCount());
    assertEquals(new BigDecimal("400.00"), eur.outstandingAmount().amount());
    assertEquals(new BigDecimal("300.00"), eur.overdueAmount().amount());
    assertEquals(new BigDecimal("100.00"), eur.currentAmount().amount());
    assertEquals(new BigDecimal("90.00"), eur.days1To30OverdueAmount().amount());
    assertEquals(new BigDecimal("80.00"), eur.days31To60OverdueAmount().amount());
    assertEquals(new BigDecimal("70.00"), eur.days61To90OverdueAmount().amount());
    assertEquals(new BigDecimal("60.00"), eur.days91PlusOverdueAmount().amount());
  }

  @Test
  void returnsEmptySummaryWithoutCrossTenantLeakage() {
    UUID emptyTenant = UUID.fromString("00000000-0000-0000-0000-000000000599");

    var result = summaryUseCase.get(new GetReceivablesSummaryQuery(emptyTenant, BUSINESS_DATE));

    assertEquals(0, result.invoiceCount());
    assertEquals(0, result.openCount());
    assertEquals(0, result.overdueCount());
    assertTrue(result.currencies().isEmpty());
  }

  private void consume(
      UUID tenantId,
      String invoiceId,
      String currency,
      String originalAmount,
      String paidAmount,
      LocalDate dueDate,
      boolean cancelled,
      String status) {
    UUID id = UUID.fromString(invoiceId);
    consumeUseCase.consume(
        new AnalyticsIntegrationEvent(
            UUID.randomUUID(),
            "operations.invoice.synchronized.v1",
            1,
            tenantId,
            "INVOICE",
            id,
            1,
            "{}",
            OCCURRED_AT,
            new AnalyticsProjectionPayload.InvoiceReceivable(
                id,
                CUSTOMER_ID,
                "INV-" + invoiceId.substring(invoiceId.length() - 3),
                new BigDecimal(originalAmount),
                new BigDecimal(paidAmount),
                currency,
                LocalDate.of(2026, 4, 1),
                dueDate,
                cancelled,
                status)));
  }
}
