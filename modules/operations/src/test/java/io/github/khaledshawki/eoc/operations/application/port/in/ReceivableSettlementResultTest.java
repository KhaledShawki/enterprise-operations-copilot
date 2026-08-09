package io.github.khaledshawki.eoc.operations.application.port.in;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.khaledshawki.eoc.operations.application.model.settlement.ReceivableAllocationResult;
import io.github.khaledshawki.eoc.operations.domain.model.BusinessPartnerId;
import io.github.khaledshawki.eoc.operations.domain.model.CurrencyCode;
import io.github.khaledshawki.eoc.operations.domain.model.InvoiceId;
import io.github.khaledshawki.eoc.operations.domain.model.Money;
import io.github.khaledshawki.eoc.operations.domain.model.OperationsTenantId;
import io.github.khaledshawki.eoc.operations.domain.model.PaymentId;
import io.github.khaledshawki.eoc.operations.domain.model.PaymentStatus;
import io.github.khaledshawki.eoc.operations.domain.model.ReceivableAllocationId;
import io.github.khaledshawki.eoc.operations.domain.model.ReceivableAllocationState;
import io.github.khaledshawki.eoc.operations.domain.model.ReceivableSettlementId;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ReceivableSettlementResultTest {

  private static final CurrencyCode EUR = CurrencyCode.of("EUR");
  private static final OperationsTenantId TENANT_ID =
      OperationsTenantId.of(UUID.fromString("00000000-0000-0000-0000-000000000001"));
  private static final PaymentId PAYMENT_ID =
      PaymentId.of(UUID.fromString("00000000-0000-0000-0000-000000000002"));
  private static final BusinessPartnerId CUSTOMER_ID =
      BusinessPartnerId.of(UUID.fromString("00000000-0000-0000-0000-000000000003"));
  private static final ReceivableSettlementId SETTLEMENT_ID =
      ReceivableSettlementId.of(UUID.fromString("00000000-0000-0000-0000-000000000004"));
  private static final InvoiceId INVOICE_ID =
      InvoiceId.of(UUID.fromString("00000000-0000-0000-0000-000000000005"));

  @Test
  void shouldAcceptAndDefensivelyCopyAConsistentSettlementProjection() {
    ArrayList<ReceivableAllocationResult> allocations =
        new ArrayList<>(List.of(allocation("60.00", ReceivableAllocationState.ACTIVE)));

    ReceivableSettlementResult result =
        new ReceivableSettlementResult(
            payment("100.00", false),
            Optional.of(SETTLEMENT_ID),
            Money.of("60.00", EUR),
            Money.of("40.00", EUR),
            allocations);
    allocations.clear();

    assertEquals(1, result.allocations().size());
    assertEquals(Money.of("60.00", EUR), result.allocatedAmount());
    assertEquals(Money.of("40.00", EUR), result.unappliedAmount());
  }

  @Test
  void shouldRepresentAPaymentWithoutLocalSettlementAsFullyUnapplied() {
    ReceivableSettlementResult result =
        new ReceivableSettlementResult(
            payment("100.00", false),
            Optional.empty(),
            Money.zero(EUR),
            Money.of("100.00", EUR),
            List.of());

    assertEquals(Optional.empty(), result.settlementId());
    assertEquals(Money.zero(EUR), result.allocatedAmount());
  }

  @Test
  void shouldRejectAllocationHistoryWhenNoSettlementExists() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new ReceivableSettlementResult(
                payment("100.00", false),
                Optional.empty(),
                Money.of("60.00", EUR),
                Money.of("40.00", EUR),
                List.of(allocation("60.00", ReceivableAllocationState.ACTIVE))));
  }

  @Test
  void shouldRejectSummaryThatDoesNotMatchActiveAllocationHistory() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new ReceivableSettlementResult(
                payment("100.00", false),
                Optional.of(SETTLEMENT_ID),
                Money.of("50.00", EUR),
                Money.of("50.00", EUR),
                List.of(allocation("60.00", ReceivableAllocationState.ACTIVE))));
  }

  @Test
  void shouldExcludeReversedHistoryFromTheActiveTotal() {
    ReceivableSettlementResult result =
        new ReceivableSettlementResult(
            payment("100.00", false),
            Optional.of(SETTLEMENT_ID),
            Money.of("60.00", EUR),
            Money.of("40.00", EUR),
            List.of(
                allocation("60.00", ReceivableAllocationState.ACTIVE),
                allocation(
                    "10.00",
                    ReceivableAllocationState.REVERSED,
                    UUID.fromString("00000000-0000-0000-0000-000000000007"))));

    assertEquals(2, result.allocations().size());
    assertEquals(Money.of("60.00", EUR), result.allocatedAmount());
  }

  @Test
  void shouldRejectAllocationOwnedByAnotherSettlement() {
    ReceivableAllocationResult wrongSettlement =
        new ReceivableAllocationResult(
            ReceivableSettlementId.of(UUID.fromString("00000000-0000-0000-0000-000000000099")),
            PAYMENT_ID,
            ReceivableAllocationId.of(UUID.fromString("00000000-0000-0000-0000-000000000006")),
            INVOICE_ID,
            Money.of("60.00", EUR),
            ReceivableAllocationState.ACTIVE);

    assertThrows(
        IllegalArgumentException.class,
        () ->
            new ReceivableSettlementResult(
                payment("100.00", false),
                Optional.of(SETTLEMENT_ID),
                Money.of("60.00", EUR),
                Money.of("40.00", EUR),
                List.of(wrongSettlement)));
  }

  @Test
  void shouldRejectAllocationOwnedByAnotherPayment() {
    ReceivableAllocationResult wrongPayment =
        new ReceivableAllocationResult(
            SETTLEMENT_ID,
            PaymentId.of(UUID.fromString("00000000-0000-0000-0000-000000000099")),
            ReceivableAllocationId.of(UUID.fromString("00000000-0000-0000-0000-000000000006")),
            INVOICE_ID,
            Money.of("60.00", EUR),
            ReceivableAllocationState.ACTIVE);

    assertThrows(
        IllegalArgumentException.class,
        () ->
            new ReceivableSettlementResult(
                payment("100.00", false),
                Optional.of(SETTLEMENT_ID),
                Money.of("60.00", EUR),
                Money.of("40.00", EUR),
                List.of(wrongPayment)));
  }

  @Test
  void shouldRejectAmountsThatDoNotReconcileToPaymentEffectiveAmount() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new ReceivableSettlementResult(
                payment("100.00", false),
                Optional.of(SETTLEMENT_ID),
                Money.of("60.00", EUR),
                Money.of("30.00", EUR),
                List.of(allocation("60.00", ReceivableAllocationState.ACTIVE))));
  }

  private static PaymentResult payment(String amount, boolean reversed) {
    Money paymentAmount = Money.of(amount, EUR);
    return new PaymentResult(
        PAYMENT_ID,
        TENANT_ID,
        CUSTOMER_ID,
        paymentAmount,
        reversed ? Money.zero(EUR) : paymentAmount,
        LocalDate.of(2026, 8, 9),
        reversed ? PaymentStatus.REVERSED : PaymentStatus.RECORDED,
        reversed);
  }

  private static ReceivableAllocationResult allocation(
      String amount, ReceivableAllocationState state) {
    return allocation(amount, state, UUID.fromString("00000000-0000-0000-0000-000000000006"));
  }

  private static ReceivableAllocationResult allocation(
      String amount, ReceivableAllocationState state, UUID allocationId) {
    return new ReceivableAllocationResult(
        SETTLEMENT_ID,
        PAYMENT_ID,
        ReceivableAllocationId.of(allocationId),
        INVOICE_ID,
        Money.of(amount, EUR),
        state);
  }
}
