package io.github.khaledshawki.eoc.operations.application.model.querying;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.khaledshawki.eoc.operations.application.model.authorization.OperationsActor;
import io.github.khaledshawki.eoc.operations.application.port.in.InvoicePageResult;
import io.github.khaledshawki.eoc.operations.application.port.in.ListInvoicesQuery;
import io.github.khaledshawki.eoc.operations.domain.model.BusinessPartnerId;
import io.github.khaledshawki.eoc.operations.domain.model.CurrencyCode;
import io.github.khaledshawki.eoc.operations.domain.model.Invoice;
import io.github.khaledshawki.eoc.operations.domain.model.InvoiceNumber;
import io.github.khaledshawki.eoc.operations.domain.model.InvoiceStatus;
import io.github.khaledshawki.eoc.operations.domain.model.Money;
import io.github.khaledshawki.eoc.operations.domain.model.OperationsTenantId;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class InvoiceQueryContractTest {

  private static final OperationsTenantId TENANT_ID =
      OperationsTenantId.of(UUID.fromString("00000000-0000-0000-0000-000000000001"));
  private static final BusinessPartnerId CUSTOMER_ID =
      BusinessPartnerId.of(UUID.fromString("00000000-0000-0000-0000-000000000002"));
  private static final LocalDate BUSINESS_DATE = LocalDate.of(2026, 8, 6);
  private static final CurrencyCode EUR = CurrencyCode.of("EUR");

  @Test
  void shouldDeriveEveryDueStateFromCanonicalInvoiceFacts() {
    assertEquals(
        InvoiceDueState.OVERDUE,
        InvoiceDueState.from(invoice("100.00", "0.00", -1, false), BUSINESS_DATE));
    assertEquals(
        InvoiceDueState.DUE_TODAY,
        InvoiceDueState.from(invoice("100.00", "0.00", 0, false), BUSINESS_DATE));
    assertEquals(
        InvoiceDueState.NOT_DUE,
        InvoiceDueState.from(invoice("100.00", "0.00", 1, false), BUSINESS_DATE));
    assertEquals(
        InvoiceDueState.SETTLED,
        InvoiceDueState.from(invoice("100.00", "100.00", -1, false), BUSINESS_DATE));
    assertEquals(
        InvoiceDueState.SETTLED,
        InvoiceDueState.from(invoice("100.00", "0.00", -1, true), BUSINESS_DATE));
  }

  @Test
  void shouldRejectInvalidPagingAndNullStatusElements() {
    OperationsActor actor = new OperationsActor("issuer", "subject");
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new ListInvoicesQuery(
                actor,
                TENANT_ID.value(),
                Optional.empty(),
                Set.of(),
                Optional.empty(),
                BUSINESS_DATE,
                -1,
                50,
                InvoiceSortField.ISSUE_DATE,
                SortDirection.DESC));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new ListInvoicesQuery(
                actor,
                TENANT_ID.value(),
                Optional.empty(),
                Set.of(),
                Optional.empty(),
                BUSINESS_DATE,
                0,
                InvoiceQueryCriteria.MAX_PAGE_SIZE + 1,
                InvoiceSortField.ISSUE_DATE,
                SortDirection.DESC));
    Set<InvoiceStatus> statusesWithNull = new HashSet<>();
    statusesWithNull.add(null);
    assertThrows(
        NullPointerException.class,
        () ->
            new ListInvoicesQuery(
                actor,
                TENANT_ID.value(),
                Optional.empty(),
                statusesWithNull,
                Optional.empty(),
                BUSINESS_DATE,
                0,
                50,
                InvoiceSortField.ISSUE_DATE,
                SortDirection.DESC));
  }

  @Test
  void shouldCalculatePageMetadataWithoutOverflowProneAddition() {
    InvoiceQueryPage first =
        new InvoiceQueryPage(List.of(invoice("10.00", "0.00", 1, false)), 0, 1, 2);
    InvoicePageResult result = InvoicePageResult.from(first, BUSINESS_DATE);
    assertEquals(2, result.totalPages());
    assertTrue(result.hasNext());
    assertFalse(result.hasPrevious());

    InvoiceQueryPage last =
        new InvoiceQueryPage(List.of(invoice("10.00", "0.00", 1, false)), 1, 1, 2);
    InvoicePageResult lastResult = InvoicePageResult.from(last, BUSINESS_DATE);
    assertFalse(lastResult.hasNext());
    assertTrue(lastResult.hasPrevious());
  }

  @Test
  void shouldPreserveFiltersInFrameworkIndependentCriteria() {
    OperationsActor actor = new OperationsActor("issuer", "subject");
    ListInvoicesQuery query =
        new ListInvoicesQuery(
            actor,
            TENANT_ID.value(),
            Optional.of(CUSTOMER_ID.value()),
            Set.of(InvoiceStatus.OPEN, InvoiceStatus.PARTIALLY_PAID),
            Optional.of(InvoiceDueState.OVERDUE),
            BUSINESS_DATE,
            2,
            25,
            InvoiceSortField.DUE_DATE,
            SortDirection.ASC);

    InvoiceQueryCriteria criteria = query.criteria();

    assertEquals(TENANT_ID, criteria.tenantId());
    assertEquals(Optional.of(CUSTOMER_ID), criteria.customerId());
    assertEquals(Set.of(InvoiceStatus.OPEN, InvoiceStatus.PARTIALLY_PAID), criteria.statuses());
    assertEquals(Optional.of(InvoiceDueState.OVERDUE), criteria.dueState());
    assertEquals(2, criteria.pageNumber());
    assertEquals(25, criteria.pageSize());
  }

  private static Invoice invoice(
      String originalAmount, String paidAmount, int dueDateOffset, boolean cancelled) {
    return Invoice.reconstitute(
        io.github.khaledshawki.eoc.operations.domain.model.InvoiceId.generate(),
        TENANT_ID,
        CUSTOMER_ID,
        new InvoiceNumber(UUID.randomUUID().toString()),
        Money.of(originalAmount, EUR),
        Money.of(paidAmount, EUR),
        BUSINESS_DATE.minusDays(10),
        BUSINESS_DATE.plusDays(dueDateOffset),
        cancelled);
  }
}
