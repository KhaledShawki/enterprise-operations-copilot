package io.github.khaledshawki.eoc.platform.copilot.adapter.out.analytics;

import static org.junit.jupiter.api.Assertions.*;

import io.github.khaledshawki.eoc.analytics.application.exception.*;
import io.github.khaledshawki.eoc.analytics.application.model.querying.ReceivableCurrencySummary;
import io.github.khaledshawki.eoc.analytics.application.model.querying.ReceivableCustomerSummary;
import io.github.khaledshawki.eoc.analytics.application.port.in.*;
import io.github.khaledshawki.eoc.analytics.domain.model.AnalyticsMoney;
import io.github.khaledshawki.eoc.analytics.domain.model.AnalyticsTenantId;
import io.github.khaledshawki.eoc.analytics.domain.model.CurrencyCode;
import io.github.khaledshawki.eoc.analytics.domain.model.InvoiceReceivableStatus;
import io.github.khaledshawki.eoc.analytics.domain.model.ProjectionCursor;
import io.github.khaledshawki.eoc.copilot.application.exception.*;
import io.github.khaledshawki.eoc.copilot.application.model.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class AnalyticsCopilotReceivablesAdapterTest {
  private static final UUID TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000111");
  private static final UUID INVOICE_ID = UUID.fromString("00000000-0000-0000-0000-000000000211");
  private static final UUID CUSTOMER_ID = UUID.fromString("00000000-0000-0000-0000-000000000311");
  private static final UUID EVENT_ID = UUID.fromString("00000000-0000-0000-0000-000000000411");
  private static final LocalDate BUSINESS_DATE = LocalDate.of(2026, 8, 12);

  @Test
  void mapsReceivableAndPreservesEvidence() {
    var adapter =
        adapter(query -> receivable(), query -> emptyPage(query), query -> emptySummary(query));
    CopilotReceivable result = adapter.getReceivable(TENANT_ID, INVOICE_ID, BUSINESS_DATE);
    assertEquals(TENANT_ID, result.tenantId());
    assertEquals(INVOICE_ID, result.invoiceId());
    assertEquals(CUSTOMER_ID, result.customer().customerId());
    assertTrue(result.customer().projected());
    assertEquals(Optional.of("C-1"), result.customer().partnerNumber());
    assertEquals(new BigDecimal("80.00"), result.outstandingAmount().amount());
    assertEquals("CHF", result.outstandingAmount().currency());
    assertTrue(result.overdue());
    assertEquals(EVENT_ID, result.evidence().eventId());
    assertEquals(3, result.evidence().aggregateVersion());
  }

  @Test
  void mapsBoundedListCriteriaToAnalyticsQuery() {
    var captured = new AtomicReference<ListReceivablesQuery>();
    ListReceivablesUseCase list =
        query -> {
          captured.set(query);
          return emptyPage(query);
        };
    var adapter = adapter(query -> receivable(), list, query -> emptySummary(query));
    var criteria =
        new ReceivableListCriteria(
            Optional.of(CUSTOMER_ID),
            Set.of(ReceivableStatus.OPEN, ReceivableStatus.PARTIALLY_PAID),
            Optional.of(true),
            BUSINESS_DATE,
            2,
            25,
            ReceivableSortField.OUTSTANDING_AMOUNT,
            SortDirection.DESC);
    CopilotReceivablePage result = adapter.listReceivables(TENANT_ID, criteria);
    assertEquals(2, result.pageNumber());
    assertEquals(25, result.pageSize());
    assertEquals(TENANT_ID, captured.get().tenantId());
    assertEquals(
        Set.of(InvoiceReceivableStatus.OPEN, InvoiceReceivableStatus.PARTIALLY_PAID),
        captured.get().statuses());
    assertEquals(
        io.github.khaledshawki.eoc.analytics.application.model.querying.ReceivableSortField
            .OUTSTANDING_AMOUNT,
        captured.get().sortField());
    assertEquals(
        io.github.khaledshawki.eoc.analytics.application.model.querying.SortDirection.DESC,
        captured.get().sortDirection());
  }

  @Test
  void mapsCurrencySeparatedSummaryWithoutCombiningMoney() {
    GetReceivablesSummaryUseCase summary =
        query ->
            new ReceivablesSummaryResult(
                query.tenantId(), query.businessDate(), 2, 2, 1, List.of(currencySummary()));
    var adapter = adapter(query -> receivable(), query -> emptyPage(query), summary);
    CopilotReceivablesSummary result = adapter.getReceivablesSummary(TENANT_ID, BUSINESS_DATE);
    assertEquals(2, result.invoiceCount());
    assertEquals(1, result.currencies().size());
    assertEquals("CHF", result.currencies().getFirst().currency());
    assertEquals(
        new BigDecimal("150.00"), result.currencies().getFirst().outstandingAmount().amount());
    assertEquals(new BigDecimal("50.00"), result.currencies().getFirst().overdueAmount().amount());
  }

  @Test
  void mapsNotFoundUnavailableAndCorruptionFailures() {
    assertThrows(
        CopilotToolDataNotFoundException.class,
        () ->
            adapter(
                    query -> {
                      throw new ReceivableNotFoundException(
                          AnalyticsTenantId.of(TENANT_ID), INVOICE_ID);
                    },
                    query -> emptyPage(query),
                    query -> emptySummary(query))
                .getReceivable(TENANT_ID, INVOICE_ID, BUSINESS_DATE));
    assertThrows(
        CopilotToolDataUnavailableException.class,
        () ->
            adapter(
                    query -> {
                      throw new AnalyticsReadUnavailableException(new IllegalStateException("db"));
                    },
                    query -> emptyPage(query),
                    query -> emptySummary(query))
                .getReceivable(TENANT_ID, INVOICE_ID, BUSINESS_DATE));
    assertThrows(
        CopilotToolDataCorruptedException.class,
        () ->
            adapter(
                    query -> {
                      throw new AnalyticsProjectionStateCorruptedException("bad row");
                    },
                    query -> emptyPage(query),
                    query -> emptySummary(query))
                .getReceivable(TENANT_ID, INVOICE_ID, BUSINESS_DATE));
  }

  private static AnalyticsCopilotReceivablesAdapter adapter(
      GetReceivableUseCase get, ListReceivablesUseCase list, GetReceivablesSummaryUseCase summary) {
    return new AnalyticsCopilotReceivablesAdapter(get, list, summary);
  }

  private static ReceivableResult receivable() {
    return new ReceivableResult(
        TENANT_ID,
        INVOICE_ID,
        new ReceivableCustomerSummary(CUSTOMER_ID, Optional.of("C-1"), Optional.of("Acme")),
        "INV-1",
        AnalyticsMoney.of(new BigDecimal("100.00"), "CHF"),
        AnalyticsMoney.of(new BigDecimal("20.00"), "CHF"),
        AnalyticsMoney.of(new BigDecimal("80.00"), "CHF"),
        LocalDate.of(2026, 6, 1),
        LocalDate.of(2026, 7, 1),
        BUSINESS_DATE,
        InvoiceReceivableStatus.PARTIALLY_PAID,
        false,
        true,
        new ProjectionCursor(EVENT_ID, 3, Instant.parse("2026-08-01T00:00:00Z")));
  }

  private static ReceivablePageResult emptyPage(ListReceivablesQuery query) {
    return new ReceivablePageResult(
        List.of(),
        query.pageNumber(),
        query.pageSize(),
        0,
        0,
        query.businessDate(),
        false,
        query.pageNumber() > 0);
  }

  private static ReceivablesSummaryResult emptySummary(GetReceivablesSummaryQuery query) {
    return new ReceivablesSummaryResult(query.tenantId(), query.businessDate(), 0, 0, 0, List.of());
  }

  private static ReceivableCurrencySummary currencySummary() {
    CurrencyCode currency = CurrencyCode.of("CHF");
    return new ReceivableCurrencySummary(
        currency,
        2,
        2,
        1,
        AnalyticsMoney.of(new BigDecimal("150.00"), "CHF"),
        AnalyticsMoney.of(new BigDecimal("50.00"), "CHF"),
        AnalyticsMoney.of(new BigDecimal("100.00"), "CHF"),
        AnalyticsMoney.of(new BigDecimal("50.00"), "CHF"),
        AnalyticsMoney.of(new BigDecimal("0.00"), "CHF"),
        AnalyticsMoney.of(new BigDecimal("0.00"), "CHF"),
        AnalyticsMoney.of(new BigDecimal("0.00"), "CHF"));
  }
}
