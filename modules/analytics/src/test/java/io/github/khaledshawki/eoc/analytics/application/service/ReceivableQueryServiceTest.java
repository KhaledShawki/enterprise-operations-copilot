package io.github.khaledshawki.eoc.analytics.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.khaledshawki.eoc.analytics.application.exception.AnalyticsProjectionStateCorruptedException;
import io.github.khaledshawki.eoc.analytics.application.exception.ReceivableNotFoundException;
import io.github.khaledshawki.eoc.analytics.application.model.querying.ReceivableCustomerSummary;
import io.github.khaledshawki.eoc.analytics.application.model.querying.ReceivablePage;
import io.github.khaledshawki.eoc.analytics.application.model.querying.ReceivableQueryCriteria;
import io.github.khaledshawki.eoc.analytics.application.model.querying.ReceivableSnapshot;
import io.github.khaledshawki.eoc.analytics.application.model.querying.ReceivableSortField;
import io.github.khaledshawki.eoc.analytics.application.model.querying.SortDirection;
import io.github.khaledshawki.eoc.analytics.application.port.in.GetReceivableQuery;
import io.github.khaledshawki.eoc.analytics.application.port.in.ListReceivablesQuery;
import io.github.khaledshawki.eoc.analytics.application.port.out.ReceivableReadPort;
import io.github.khaledshawki.eoc.analytics.domain.model.AnalyticsMoney;
import io.github.khaledshawki.eoc.analytics.domain.model.AnalyticsTenantId;
import io.github.khaledshawki.eoc.analytics.domain.model.CurrencyCode;
import io.github.khaledshawki.eoc.analytics.domain.model.InvoiceReceivableProjection;
import io.github.khaledshawki.eoc.analytics.domain.model.InvoiceReceivableStatus;
import io.github.khaledshawki.eoc.analytics.domain.model.ProjectionCursor;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ReceivableQueryServiceTest {

  private static final UUID TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
  private static final UUID OTHER_TENANT_ID =
      UUID.fromString("00000000-0000-0000-0000-000000000002");
  private static final UUID INVOICE_ID = UUID.fromString("00000000-0000-0000-0000-000000000010");
  private static final UUID CUSTOMER_ID = UUID.fromString("00000000-0000-0000-0000-000000000020");
  private static final LocalDate BUSINESS_DATE = LocalDate.of(2026, 8, 12);

  @Test
  void getsReceivableWithCustomerEvidenceAndDerivedOverdueState() {
    RecordingReadPort port = new RecordingReadPort();
    port.single = Optional.of(snapshot(TENANT_ID, INVOICE_ID, true));
    ReceivableQueryService service = new ReceivableQueryService(port);

    var result = service.get(new GetReceivableQuery(TENANT_ID, INVOICE_ID, BUSINESS_DATE));

    assertEquals(INVOICE_ID, result.invoiceId());
    assertEquals(new BigDecimal("75.00"), result.outstandingAmount().amount());
    assertTrue(result.overdue());
    assertTrue(result.customer().projected());
    assertEquals("Acme AG", result.customer().displayName().orElseThrow());
    assertEquals(AnalyticsTenantId.of(TENANT_ID), port.findTenantId);
    assertEquals(INVOICE_ID, port.findInvoiceId);
  }

  @Test
  void keepsReceivableReadableWhenCustomerProjectionHasNotArrivedYet() {
    RecordingReadPort port = new RecordingReadPort();
    port.single = Optional.of(snapshot(TENANT_ID, INVOICE_ID, false));
    ReceivableQueryService service = new ReceivableQueryService(port);

    var result = service.get(new GetReceivableQuery(TENANT_ID, INVOICE_ID, BUSINESS_DATE));

    assertFalse(result.customer().projected());
    assertTrue(result.customer().displayName().isEmpty());
  }

  @Test
  void reportsMissingReceivableWithoutCrossTenantFallback() {
    RecordingReadPort port = new RecordingReadPort();
    ReceivableQueryService service = new ReceivableQueryService(port);

    assertThrows(
        ReceivableNotFoundException.class,
        () -> service.get(new GetReceivableQuery(TENANT_ID, INVOICE_ID, BUSINESS_DATE)));

    assertEquals(AnalyticsTenantId.of(TENANT_ID), port.findTenantId);
    assertEquals(INVOICE_ID, port.findInvoiceId);
  }

  @Test
  void listsUsingExactCriteriaAndReturnsStablePageMetadata() {
    RecordingReadPort port = new RecordingReadPort();
    port.page = new ReceivablePage(List.of(snapshot(TENANT_ID, INVOICE_ID, true)), 1, 25, 26);
    ReceivableQueryService service = new ReceivableQueryService(port);
    ListReceivablesQuery query =
        new ListReceivablesQuery(
            TENANT_ID,
            Optional.of(CUSTOMER_ID),
            Set.of(InvoiceReceivableStatus.OPEN, InvoiceReceivableStatus.PARTIALLY_PAID),
            Optional.of(true),
            BUSINESS_DATE,
            1,
            25,
            ReceivableSortField.DUE_DATE,
            SortDirection.ASC);

    var result = service.list(query);

    assertEquals(query.criteria(), port.criteria);
    assertEquals(26, result.totalElements());
    assertEquals(2, result.totalPages());
    assertFalse(result.hasNext());
    assertTrue(result.hasPrevious());
    assertEquals(BUSINESS_DATE, result.businessDate());
    assertTrue(result.receivables().getFirst().overdue());
  }

  @Test
  void rejectsReadAdapterCrossTenantLeakage() {
    RecordingReadPort port = new RecordingReadPort();
    port.single = Optional.of(snapshot(OTHER_TENANT_ID, INVOICE_ID, true));
    ReceivableQueryService service = new ReceivableQueryService(port);

    assertThrows(
        AnalyticsProjectionStateCorruptedException.class,
        () -> service.get(new GetReceivableQuery(TENANT_ID, INVOICE_ID, BUSINESS_DATE)));
  }

  @Test
  void rejectsDuplicateInvoicesReturnedByReadAdapter() {
    ReceivableSnapshot duplicate = snapshot(TENANT_ID, INVOICE_ID, true);
    RecordingReadPort port = new RecordingReadPort();
    port.page = new ReceivablePage(List.of(duplicate, duplicate), 0, 10, 2);
    ReceivableQueryService service = new ReceivableQueryService(port);

    assertThrows(
        AnalyticsProjectionStateCorruptedException.class,
        () ->
            service.list(
                new ListReceivablesQuery(
                    TENANT_ID,
                    Optional.empty(),
                    Set.of(),
                    Optional.empty(),
                    BUSINESS_DATE,
                    0,
                    10,
                    ReceivableSortField.DUE_DATE,
                    SortDirection.ASC)));
  }

  @Test
  void rejectsInvalidPageSizeAtTheApplicationBoundary() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new ListReceivablesQuery(
                TENANT_ID,
                Optional.empty(),
                Set.of(),
                Optional.empty(),
                BUSINESS_DATE,
                0,
                ReceivableQueryCriteria.MAX_PAGE_SIZE + 1,
                ReceivableSortField.DUE_DATE,
                SortDirection.ASC));
  }

  private static ReceivableSnapshot snapshot(
      UUID tenantId, UUID invoiceId, boolean customerProjected) {
    CurrencyCode eur = CurrencyCode.of("EUR");
    InvoiceReceivableProjection invoice =
        new InvoiceReceivableProjection(
            AnalyticsTenantId.of(tenantId),
            invoiceId,
            CUSTOMER_ID,
            "INV-100",
            new AnalyticsMoney(new BigDecimal("100.00"), eur),
            new AnalyticsMoney(new BigDecimal("25.00"), eur),
            LocalDate.of(2026, 7, 1),
            LocalDate.of(2026, 8, 1),
            false,
            InvoiceReceivableStatus.PARTIALLY_PAID,
            new ProjectionCursor(
                UUID.fromString("00000000-0000-0000-0000-000000000030"),
                2,
                Instant.parse("2026-08-02T12:00:00Z")));
    ReceivableCustomerSummary customer =
        customerProjected
            ? new ReceivableCustomerSummary(
                CUSTOMER_ID, Optional.of("C-100"), Optional.of("Acme AG"))
            : new ReceivableCustomerSummary(CUSTOMER_ID, Optional.empty(), Optional.empty());
    return new ReceivableSnapshot(invoice, customer);
  }

  private static final class RecordingReadPort implements ReceivableReadPort {
    private Optional<ReceivableSnapshot> single = Optional.empty();
    private ReceivablePage page = new ReceivablePage(List.of(), 0, 50, 0);
    private AnalyticsTenantId findTenantId;
    private UUID findInvoiceId;
    private ReceivableQueryCriteria criteria;

    @Override
    public Optional<ReceivableSnapshot> findById(AnalyticsTenantId tenantId, UUID invoiceId) {
      findTenantId = tenantId;
      findInvoiceId = invoiceId;
      return single;
    }

    @Override
    public ReceivablePage findPage(ReceivableQueryCriteria criteria) {
      this.criteria = criteria;
      return page;
    }
  }
}
