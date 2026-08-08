package io.github.khaledshawki.eoc.operations.application.model.querying;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.khaledshawki.eoc.operations.application.model.authorization.OperationsActor;
import io.github.khaledshawki.eoc.operations.application.port.in.ListPaymentsQuery;
import io.github.khaledshawki.eoc.operations.application.port.in.PaymentPageResult;
import io.github.khaledshawki.eoc.operations.application.port.in.PaymentResult;
import io.github.khaledshawki.eoc.operations.domain.model.BusinessPartnerId;
import io.github.khaledshawki.eoc.operations.domain.model.CurrencyCode;
import io.github.khaledshawki.eoc.operations.domain.model.Money;
import io.github.khaledshawki.eoc.operations.domain.model.OperationsTenantId;
import io.github.khaledshawki.eoc.operations.domain.model.Payment;
import io.github.khaledshawki.eoc.operations.domain.model.PaymentId;
import io.github.khaledshawki.eoc.operations.domain.model.PaymentStatus;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PaymentQueryContractTest {

  private static final OperationsTenantId TENANT_ID =
      OperationsTenantId.of(UUID.fromString("00000000-0000-0000-0000-000000000001"));
  private static final BusinessPartnerId CUSTOMER_ID =
      BusinessPartnerId.of(UUID.fromString("00000000-0000-0000-0000-000000000002"));
  private static final OperationsActor ACTOR = new OperationsActor("issuer", "subject");
  private static final CurrencyCode EUR = CurrencyCode.of("EUR");

  @Test
  void shouldPreserveFiltersInFrameworkIndependentCriteria() {
    LocalDate from = LocalDate.of(2026, 8, 1);
    LocalDate to = LocalDate.of(2026, 8, 31);
    ListPaymentsQuery query =
        new ListPaymentsQuery(
            ACTOR,
            TENANT_ID.value(),
            Optional.of(CUSTOMER_ID.value()),
            Set.of(PaymentStatus.RECORDED, PaymentStatus.REVERSED),
            Optional.of(from),
            Optional.of(to),
            2,
            25,
            PaymentSortField.PAYMENT_DATE,
            SortDirection.ASC);

    PaymentQueryCriteria criteria = query.criteria();

    assertEquals(TENANT_ID, criteria.tenantId());
    assertEquals(Optional.of(CUSTOMER_ID), criteria.customerId());
    assertEquals(Set.of(PaymentStatus.RECORDED, PaymentStatus.REVERSED), criteria.statuses());
    assertEquals(Optional.of(from), criteria.paymentDateFrom());
    assertEquals(Optional.of(to), criteria.paymentDateTo());
    assertEquals(2, criteria.pageNumber());
    assertEquals(25, criteria.pageSize());
  }

  @Test
  void shouldRejectInvalidDateRangePagingAndNullStatusElements() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            query(
                Optional.of(LocalDate.of(2026, 8, 2)),
                Optional.of(LocalDate.of(2026, 8, 1)),
                0,
                50,
                Set.of()));
    assertThrows(
        IllegalArgumentException.class,
        () -> query(Optional.empty(), Optional.empty(), -1, 50, Set.of()));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            query(
                Optional.empty(),
                Optional.empty(),
                0,
                PaymentQueryCriteria.MAX_PAGE_SIZE + 1,
                Set.of()));
    Set<PaymentStatus> statusesWithNull = new HashSet<>();
    statusesWithNull.add(null);
    assertThrows(
        NullPointerException.class,
        () -> query(Optional.empty(), Optional.empty(), 0, 50, statusesWithNull));
  }

  @Test
  void shouldCalculatePageMetadataAndRejectUnsupportedPageCount() {
    Payment payment = payment(false);
    PaymentQueryPage first = new PaymentQueryPage(List.of(payment), 0, 1, 2);
    PaymentPageResult result = PaymentPageResult.from(first);
    assertEquals(2, result.totalPages());
    assertTrue(result.hasNext());
    assertFalse(result.hasPrevious());

    PaymentQueryPage last = new PaymentQueryPage(List.of(payment), 1, 1, 2);
    PaymentPageResult lastResult = PaymentPageResult.from(last);
    assertFalse(lastResult.hasNext());
    assertTrue(lastResult.hasPrevious());

    PaymentQueryPage unsupported =
        new PaymentQueryPage(List.of(), 0, 1, (long) Integer.MAX_VALUE + 1);
    assertThrows(IllegalStateException.class, unsupported::totalPages);
  }

  @Test
  void shouldExposeCanonicalEffectiveAmountAndStatus() {
    PaymentResult recorded = PaymentResult.from(payment(false));
    PaymentResult reversed = PaymentResult.from(payment(true));

    assertEquals(PaymentStatus.RECORDED, recorded.status());
    assertEquals(Money.of("100.00", EUR), recorded.effectiveAmount());
    assertEquals(PaymentStatus.REVERSED, reversed.status());
    assertEquals(Money.zero(EUR), reversed.effectiveAmount());
  }

  @Test
  void shouldRejectInconsistentPaymentResults() {
    Payment payment = payment(false);
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new PaymentResult(
                payment.id(),
                payment.tenantId(),
                payment.customerId(),
                payment.amount(),
                Money.zero(EUR),
                payment.paymentDate(),
                PaymentStatus.RECORDED,
                false));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new PaymentResult(
                payment.id(),
                payment.tenantId(),
                payment.customerId(),
                payment.amount(),
                payment.amount(),
                payment.paymentDate(),
                PaymentStatus.REVERSED,
                false));
  }

  private static ListPaymentsQuery query(
      Optional<LocalDate> from,
      Optional<LocalDate> to,
      int page,
      int size,
      Set<PaymentStatus> statuses) {
    return new ListPaymentsQuery(
        ACTOR,
        TENANT_ID.value(),
        Optional.empty(),
        statuses,
        from,
        to,
        page,
        size,
        PaymentSortField.PAYMENT_DATE,
        SortDirection.DESC);
  }

  private static Payment payment(boolean reversed) {
    return Payment.reconstitute(
        PaymentId.generate(),
        TENANT_ID,
        CUSTOMER_ID,
        Money.of("100.00", EUR),
        LocalDate.of(2026, 8, 6),
        reversed);
  }
}
