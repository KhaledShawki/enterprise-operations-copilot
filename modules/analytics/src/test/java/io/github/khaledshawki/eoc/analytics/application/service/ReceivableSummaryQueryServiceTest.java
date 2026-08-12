package io.github.khaledshawki.eoc.analytics.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.khaledshawki.eoc.analytics.application.exception.AnalyticsProjectionStateCorruptedException;
import io.github.khaledshawki.eoc.analytics.application.model.querying.ReceivableCurrencySummary;
import io.github.khaledshawki.eoc.analytics.application.model.querying.ReceivableSummarySnapshot;
import io.github.khaledshawki.eoc.analytics.application.port.in.GetReceivablesSummaryQuery;
import io.github.khaledshawki.eoc.analytics.application.port.out.ReceivableSummaryReadPort;
import io.github.khaledshawki.eoc.analytics.domain.model.AnalyticsMoney;
import io.github.khaledshawki.eoc.analytics.domain.model.AnalyticsTenantId;
import io.github.khaledshawki.eoc.analytics.domain.model.CurrencyCode;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ReceivableSummaryQueryServiceTest {

  private static final UUID TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
  private static final UUID OTHER_TENANT_ID =
      UUID.fromString("00000000-0000-0000-0000-000000000002");
  private static final LocalDate BUSINESS_DATE = LocalDate.of(2026, 8, 12);

  @Test
  void returnsCurrencySeparatedTotalsAndAggregateCounts() {
    RecordingSummaryReadPort port = new RecordingSummaryReadPort();
    port.snapshot =
        new ReceivableSummarySnapshot(
            AnalyticsTenantId.of(TENANT_ID),
            BUSINESS_DATE,
            List.of(
                summary(
                    "CHF", 2, 2, 1, "500.00", "200.00", "300.00", "200.00", "0.00", "0.00", "0.00"),
                summary(
                    "EUR", 7, 5, 4, "400.00", "300.00", "100.00", "90.00", "80.00", "70.00",
                    "60.00")));
    ReceivableSummaryQueryService service = new ReceivableSummaryQueryService(port);

    var result = service.get(new GetReceivablesSummaryQuery(TENANT_ID, BUSINESS_DATE));

    assertEquals(9, result.invoiceCount());
    assertEquals(7, result.openCount());
    assertEquals(5, result.overdueCount());
    assertEquals(
        List.of("CHF", "EUR"),
        result.currencies().stream().map(c -> c.currency().value()).toList());
    assertEquals(new BigDecimal("300.00"), result.currencies().get(1).overdueAmount().amount());
    assertEquals(AnalyticsTenantId.of(TENANT_ID), port.tenantId);
    assertEquals(BUSINESS_DATE, port.businessDate);
  }

  @Test
  void returnsZeroCountsForTenantWithoutReceivables() {
    RecordingSummaryReadPort port = new RecordingSummaryReadPort();
    port.snapshot =
        new ReceivableSummarySnapshot(AnalyticsTenantId.of(TENANT_ID), BUSINESS_DATE, List.of());
    ReceivableSummaryQueryService service = new ReceivableSummaryQueryService(port);

    var result = service.get(new GetReceivablesSummaryQuery(TENANT_ID, BUSINESS_DATE));

    assertEquals(0, result.invoiceCount());
    assertEquals(0, result.openCount());
    assertEquals(0, result.overdueCount());
    assertTrue(result.currencies().isEmpty());
  }

  @Test
  void rejectsSummaryForDifferentTenantOrBusinessDate() {
    RecordingSummaryReadPort wrongTenant = new RecordingSummaryReadPort();
    wrongTenant.snapshot =
        new ReceivableSummarySnapshot(
            AnalyticsTenantId.of(OTHER_TENANT_ID), BUSINESS_DATE, List.of());
    RecordingSummaryReadPort wrongDate = new RecordingSummaryReadPort();
    wrongDate.snapshot =
        new ReceivableSummarySnapshot(
            AnalyticsTenantId.of(TENANT_ID), BUSINESS_DATE.minusDays(1), List.of());

    assertThrows(
        AnalyticsProjectionStateCorruptedException.class,
        () ->
            new ReceivableSummaryQueryService(wrongTenant)
                .get(new GetReceivablesSummaryQuery(TENANT_ID, BUSINESS_DATE)));
    assertThrows(
        AnalyticsProjectionStateCorruptedException.class,
        () ->
            new ReceivableSummaryQueryService(wrongDate)
                .get(new GetReceivablesSummaryQuery(TENANT_ID, BUSINESS_DATE)));
  }

  @Test
  void rejectsDuplicateOrUnsortedCurrenciesFromReadAdapter() {
    RecordingSummaryReadPort port = new RecordingSummaryReadPort();
    port.snapshot =
        new ReceivableSummarySnapshot(
            AnalyticsTenantId.of(TENANT_ID),
            BUSINESS_DATE,
            List.of(
                summary("EUR", 1, 1, 0, "10.00", "0.00", "10.00", "0.00", "0.00", "0.00", "0.00"),
                summary("CHF", 1, 1, 0, "20.00", "0.00", "20.00", "0.00", "0.00", "0.00", "0.00")));

    assertThrows(
        AnalyticsProjectionStateCorruptedException.class,
        () ->
            new ReceivableSummaryQueryService(port)
                .get(new GetReceivablesSummaryQuery(TENANT_ID, BUSINESS_DATE)));
  }

  @Test
  void rejectsFinanciallyInconsistentCurrencySummary() {
    CurrencyCode eur = CurrencyCode.of("EUR");

    assertThrows(
        IllegalArgumentException.class,
        () ->
            new ReceivableCurrencySummary(
                eur,
                1,
                1,
                1,
                money("100.00", eur),
                money("80.00", eur),
                money("20.00", eur),
                money("70.00", eur),
                money("0.00", eur),
                money("0.00", eur),
                money("0.00", eur)));
  }

  private static ReceivableCurrencySummary summary(
      String currency,
      long invoiceCount,
      long openCount,
      long overdueCount,
      String outstanding,
      String overdue,
      String current,
      String days1To30,
      String days31To60,
      String days61To90,
      String days91Plus) {
    CurrencyCode code = CurrencyCode.of(currency);
    return new ReceivableCurrencySummary(
        code,
        invoiceCount,
        openCount,
        overdueCount,
        money(outstanding, code),
        money(overdue, code),
        money(current, code),
        money(days1To30, code),
        money(days31To60, code),
        money(days61To90, code),
        money(days91Plus, code));
  }

  private static AnalyticsMoney money(String amount, CurrencyCode currency) {
    return new AnalyticsMoney(new BigDecimal(amount), currency);
  }

  private static final class RecordingSummaryReadPort implements ReceivableSummaryReadPort {
    private ReceivableSummarySnapshot snapshot;
    private AnalyticsTenantId tenantId;
    private LocalDate businessDate;

    @Override
    public ReceivableSummarySnapshot summarize(AnalyticsTenantId tenantId, LocalDate businessDate) {
      this.tenantId = tenantId;
      this.businessDate = businessDate;
      return snapshot;
    }
  }
}
