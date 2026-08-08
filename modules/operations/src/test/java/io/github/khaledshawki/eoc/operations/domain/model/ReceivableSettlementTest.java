package io.github.khaledshawki.eoc.operations.domain.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ReceivableSettlementTest {

  private static final OperationsTenantId TENANT_ID =
      OperationsTenantId.of(UUID.fromString("00000000-0000-0000-0000-000000000001"));
  private static final OperationsTenantId OTHER_TENANT_ID =
      OperationsTenantId.of(UUID.fromString("00000000-0000-0000-0000-000000000002"));
  private static final BusinessPartnerId CUSTOMER_ID =
      BusinessPartnerId.of(UUID.fromString("00000000-0000-0000-0000-000000000101"));
  private static final BusinessPartnerId OTHER_CUSTOMER_ID =
      BusinessPartnerId.of(UUID.fromString("00000000-0000-0000-0000-000000000102"));
  private static final PaymentId PAYMENT_ID =
      PaymentId.of(UUID.fromString("00000000-0000-0000-0000-000000000501"));
  private static final PaymentId OTHER_PAYMENT_ID =
      PaymentId.of(UUID.fromString("00000000-0000-0000-0000-000000000502"));
  private static final InvoiceId INVOICE_ID =
      InvoiceId.of(UUID.fromString("00000000-0000-0000-0000-000000000601"));
  private static final InvoiceId OTHER_INVOICE_ID =
      InvoiceId.of(UUID.fromString("00000000-0000-0000-0000-000000000602"));
  private static final ReceivableSettlementId SETTLEMENT_ID =
      ReceivableSettlementId.of(UUID.fromString("00000000-0000-0000-0000-000000000701"));
  private static final ReceivableAllocationId ALLOCATION_ID =
      ReceivableAllocationId.of(UUID.fromString("00000000-0000-0000-0000-000000000801"));
  private static final ReceivableAllocationId OTHER_ALLOCATION_ID =
      ReceivableAllocationId.of(UUID.fromString("00000000-0000-0000-0000-000000000802"));
  private static final CurrencyCode EUR = CurrencyCode.of("EUR");
  private static final CurrencyCode USD = CurrencyCode.of("USD");
  private static final LocalDate PAYMENT_DATE = LocalDate.of(2026, 8, 8);
  private static final LocalDate ISSUE_DATE = LocalDate.of(2026, 7, 1);
  private static final LocalDate DUE_DATE = LocalDate.of(2026, 8, 1);

  @Test
  void shouldOpenSettlementFromRecordedPayment() {
    Payment payment = payment(PAYMENT_ID, TENANT_ID, CUSTOMER_ID, "100.00", EUR, false);

    ReceivableSettlement settlement = ReceivableSettlement.open(payment);
    ReceivableSettlement another = ReceivableSettlement.open(payment);

    assertNotEquals(settlement.id(), another.id());
    assertEquals(TENANT_ID, settlement.tenantId());
    assertEquals(CUSTOMER_ID, settlement.customerId());
    assertEquals(PAYMENT_ID, settlement.paymentId());
    assertEquals(EUR, settlement.currency());
    assertEquals(List.of(), settlement.allocations());
    assertEquals(Money.zero(EUR), settlement.allocatedAmount());
    assertEquals(Money.of("100.00", EUR), settlement.unappliedAmount(payment));
  }

  @Test
  void shouldRejectOpeningSettlementForMissingOrReversedPayment() {
    assertThrows(NullPointerException.class, () -> ReceivableSettlement.open(null));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            ReceivableSettlement.open(
                payment(PAYMENT_ID, TENANT_ID, CUSTOMER_ID, "100.00", EUR, true)));
  }

  @Test
  void shouldAllocatePartialPaymentAndDeriveUnappliedAmount() {
    Payment payment = payment(PAYMENT_ID, TENANT_ID, CUSTOMER_ID, "100.00", EUR, false);
    Invoice invoice = invoice(INVOICE_ID, TENANT_ID, CUSTOMER_ID, "100.00", "0.00", EUR, false);
    ReceivableSettlement settlement = ReceivableSettlement.open(payment);

    ReceivableAllocation allocation =
        settlement.allocate(ALLOCATION_ID, payment, invoice, Money.of("40.00", EUR));

    assertEquals(ALLOCATION_ID, allocation.id());
    assertEquals(INVOICE_ID, allocation.invoiceId());
    assertEquals(Money.of("40.00", EUR), allocation.amount());
    assertEquals(ReceivableAllocationState.ACTIVE, allocation.state());
    assertTrue(allocation.active());
    assertEquals(Money.of("40.00", EUR), settlement.allocatedAmount());
    assertEquals(Money.of("40.00", EUR), settlement.allocatedAmountForInvoice(INVOICE_ID));
    assertEquals(Money.of("60.00", EUR), settlement.unappliedAmount(payment));
  }

  @Test
  void shouldAllocateOnePaymentAcrossMultipleInvoices() {
    Payment payment = payment(PAYMENT_ID, TENANT_ID, CUSTOMER_ID, "100.00", EUR, false);
    Invoice first = invoice(INVOICE_ID, TENANT_ID, CUSTOMER_ID, "80.00", "0.00", EUR, false);
    Invoice second = invoice(OTHER_INVOICE_ID, TENANT_ID, CUSTOMER_ID, "50.00", "0.00", EUR, false);
    ReceivableSettlement settlement = ReceivableSettlement.open(payment);

    settlement.allocate(ALLOCATION_ID, payment, first, Money.of("60.00", EUR));
    settlement.allocate(OTHER_ALLOCATION_ID, payment, second, Money.of("40.00", EUR));

    assertEquals(Money.of("100.00", EUR), settlement.allocatedAmount());
    assertEquals(Money.of("60.00", EUR), settlement.allocatedAmountForInvoice(INVOICE_ID));
    assertEquals(Money.of("40.00", EUR), settlement.allocatedAmountForInvoice(OTHER_INVOICE_ID));
    assertEquals(Money.zero(EUR), settlement.unappliedAmount(payment));
  }

  @Test
  void shouldAllowMultipleAllocationActionsToTheSameInvoiceWithinItsOriginalAmount() {
    Payment payment = payment(PAYMENT_ID, TENANT_ID, CUSTOMER_ID, "100.00", EUR, false);
    Invoice invoice = invoice(INVOICE_ID, TENANT_ID, CUSTOMER_ID, "100.00", "0.00", EUR, false);
    ReceivableSettlement settlement = ReceivableSettlement.open(payment);

    settlement.allocate(ALLOCATION_ID, payment, invoice, Money.of("25.00", EUR));
    settlement.allocate(OTHER_ALLOCATION_ID, payment, invoice, Money.of("35.00", EUR));

    assertEquals(Money.of("60.00", EUR), settlement.allocatedAmountForInvoice(INVOICE_ID));
    assertEquals(2, settlement.allocations().size());
  }

  @Test
  void shouldKeepSourcePaidAmountIndependentFromLocalAllocationCapacity() {
    Payment payment = payment(PAYMENT_ID, TENANT_ID, CUSTOMER_ID, "100.00", EUR, false);
    Invoice invoice = invoice(INVOICE_ID, TENANT_ID, CUSTOMER_ID, "100.00", "90.00", EUR, false);
    ReceivableSettlement settlement = ReceivableSettlement.open(payment);

    settlement.allocate(ALLOCATION_ID, payment, invoice, Money.of("100.00", EUR));

    assertEquals(Money.of("90.00", EUR), invoice.paidAmount());
    assertEquals(Money.of("100.00", EUR), settlement.allocatedAmountForInvoice(INVOICE_ID));
    assertEquals(Money.zero(EUR), settlement.unappliedAmount(payment));
  }

  @Test
  void shouldRejectZeroNegativeAndForeignCurrencyAllocationAmounts() {
    Payment payment = payment(PAYMENT_ID, TENANT_ID, CUSTOMER_ID, "100.00", EUR, false);
    Invoice invoice = invoice(INVOICE_ID, TENANT_ID, CUSTOMER_ID, "100.00", "0.00", EUR, false);
    ReceivableSettlement settlement = ReceivableSettlement.open(payment);

    assertThrows(
        IllegalArgumentException.class,
        () -> settlement.allocate(ALLOCATION_ID, payment, invoice, Money.zero(EUR)));
    assertThrows(
        IllegalArgumentException.class,
        () -> settlement.allocate(ALLOCATION_ID, payment, invoice, Money.of("-0.01", EUR)));
    assertThrows(
        IllegalArgumentException.class,
        () -> settlement.allocate(ALLOCATION_ID, payment, invoice, Money.of("1.00", USD)));

    assertEquals(List.of(), settlement.allocations());
  }

  @Test
  void shouldRejectAllocationBeyondPaymentCapacityWithoutMutation() {
    Payment payment = payment(PAYMENT_ID, TENANT_ID, CUSTOMER_ID, "100.00", EUR, false);
    Invoice first = invoice(INVOICE_ID, TENANT_ID, CUSTOMER_ID, "100.00", "0.00", EUR, false);
    Invoice second =
        invoice(OTHER_INVOICE_ID, TENANT_ID, CUSTOMER_ID, "100.00", "0.00", EUR, false);
    ReceivableSettlement settlement = ReceivableSettlement.open(payment);
    settlement.allocate(ALLOCATION_ID, payment, first, Money.of("90.00", EUR));

    assertThrows(
        IllegalArgumentException.class,
        () -> settlement.allocate(OTHER_ALLOCATION_ID, payment, second, Money.of("10.01", EUR)));

    assertEquals(1, settlement.allocations().size());
    assertEquals(Money.of("90.00", EUR), settlement.allocatedAmount());
  }

  @Test
  void shouldRejectAllocationBeyondInvoiceCapacityWithinSettlementWithoutMutation() {
    Payment payment = payment(PAYMENT_ID, TENANT_ID, CUSTOMER_ID, "200.00", EUR, false);
    Invoice invoice = invoice(INVOICE_ID, TENANT_ID, CUSTOMER_ID, "100.00", "0.00", EUR, false);
    ReceivableSettlement settlement = ReceivableSettlement.open(payment);
    settlement.allocate(ALLOCATION_ID, payment, invoice, Money.of("90.00", EUR));

    assertThrows(
        IllegalArgumentException.class,
        () -> settlement.allocate(OTHER_ALLOCATION_ID, payment, invoice, Money.of("10.01", EUR)));

    assertEquals(Money.of("90.00", EUR), settlement.allocatedAmountForInvoice(INVOICE_ID));
  }

  @Test
  void shouldRejectDuplicateAllocationIdentityWithoutMutation() {
    Payment payment = payment(PAYMENT_ID, TENANT_ID, CUSTOMER_ID, "100.00", EUR, false);
    Invoice first = invoice(INVOICE_ID, TENANT_ID, CUSTOMER_ID, "100.00", "0.00", EUR, false);
    Invoice second =
        invoice(OTHER_INVOICE_ID, TENANT_ID, CUSTOMER_ID, "100.00", "0.00", EUR, false);
    ReceivableSettlement settlement = ReceivableSettlement.open(payment);
    settlement.allocate(ALLOCATION_ID, payment, first, Money.of("25.00", EUR));

    assertThrows(
        IllegalArgumentException.class,
        () -> settlement.allocate(ALLOCATION_ID, payment, second, Money.of("10.00", EUR)));

    assertEquals(1, settlement.allocations().size());
    assertEquals(Money.of("25.00", EUR), settlement.allocatedAmount());
  }

  @Test
  void shouldRejectMismatchedPaymentIdentityTenantCustomerCurrencyAndReversal() {
    Payment payment = payment(PAYMENT_ID, TENANT_ID, CUSTOMER_ID, "100.00", EUR, false);
    Invoice invoice = invoice(INVOICE_ID, TENANT_ID, CUSTOMER_ID, "100.00", "0.00", EUR, false);
    ReceivableSettlement settlement = ReceivableSettlement.open(payment);

    assertThrows(
        IllegalArgumentException.class,
        () ->
            settlement.allocate(
                ALLOCATION_ID,
                payment(OTHER_PAYMENT_ID, TENANT_ID, CUSTOMER_ID, "100.00", EUR, false),
                invoice,
                Money.of("1.00", EUR)));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            settlement.allocate(
                ALLOCATION_ID,
                payment(PAYMENT_ID, OTHER_TENANT_ID, CUSTOMER_ID, "100.00", EUR, false),
                invoice,
                Money.of("1.00", EUR)));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            settlement.allocate(
                ALLOCATION_ID,
                payment(PAYMENT_ID, TENANT_ID, OTHER_CUSTOMER_ID, "100.00", EUR, false),
                invoice,
                Money.of("1.00", EUR)));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            settlement.allocate(
                ALLOCATION_ID,
                payment(PAYMENT_ID, TENANT_ID, CUSTOMER_ID, "100.00", USD, false),
                invoice,
                Money.of("1.00", EUR)));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            settlement.allocate(
                ALLOCATION_ID,
                payment(PAYMENT_ID, TENANT_ID, CUSTOMER_ID, "100.00", EUR, true),
                invoice,
                Money.of("1.00", EUR)));

    assertEquals(List.of(), settlement.allocations());
  }

  @Test
  void shouldRejectMismatchedInvoiceTenantCustomerCurrencyAndCancellation() {
    Payment payment = payment(PAYMENT_ID, TENANT_ID, CUSTOMER_ID, "100.00", EUR, false);
    ReceivableSettlement settlement = ReceivableSettlement.open(payment);

    assertThrows(
        IllegalArgumentException.class,
        () ->
            settlement.allocate(
                ALLOCATION_ID,
                payment,
                invoice(INVOICE_ID, OTHER_TENANT_ID, CUSTOMER_ID, "100.00", "0.00", EUR, false),
                Money.of("1.00", EUR)));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            settlement.allocate(
                ALLOCATION_ID,
                payment,
                invoice(INVOICE_ID, TENANT_ID, OTHER_CUSTOMER_ID, "100.00", "0.00", EUR, false),
                Money.of("1.00", EUR)));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            settlement.allocate(
                ALLOCATION_ID,
                payment,
                invoice(INVOICE_ID, TENANT_ID, CUSTOMER_ID, "100.00", "0.00", USD, false),
                Money.of("1.00", EUR)));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            settlement.allocate(
                ALLOCATION_ID,
                payment,
                invoice(INVOICE_ID, TENANT_ID, CUSTOMER_ID, "100.00", "0.00", EUR, true),
                Money.of("1.00", EUR)));

    assertEquals(List.of(), settlement.allocations());
  }

  @Test
  void shouldReverseAllocationIdempotentlyAndPreserveHistory() {
    Payment payment = payment(PAYMENT_ID, TENANT_ID, CUSTOMER_ID, "100.00", EUR, false);
    Invoice invoice = invoice(INVOICE_ID, TENANT_ID, CUSTOMER_ID, "100.00", "0.00", EUR, false);
    ReceivableSettlement settlement = ReceivableSettlement.open(payment);
    settlement.allocate(ALLOCATION_ID, payment, invoice, Money.of("40.00", EUR));

    ReceivableAllocation reversed = settlement.reverseAllocation(ALLOCATION_ID);
    ReceivableAllocation replay = settlement.reverseAllocation(ALLOCATION_ID);

    assertEquals(ReceivableAllocationState.REVERSED, reversed.state());
    assertFalse(reversed.active());
    assertSame(reversed, replay);
    assertEquals(1, settlement.allocations().size());
    assertEquals(ReceivableAllocationState.REVERSED, settlement.allocations().getFirst().state());
    assertEquals(Money.zero(EUR), settlement.allocatedAmount());
    assertEquals(Money.of("100.00", EUR), settlement.unappliedAmount(payment));
  }

  @Test
  void shouldAllowNewAllocationAfterPreviousAllocationIsReversed() {
    Payment payment = payment(PAYMENT_ID, TENANT_ID, CUSTOMER_ID, "100.00", EUR, false);
    Invoice invoice = invoice(INVOICE_ID, TENANT_ID, CUSTOMER_ID, "100.00", "0.00", EUR, false);
    ReceivableSettlement settlement = ReceivableSettlement.open(payment);
    settlement.allocate(ALLOCATION_ID, payment, invoice, Money.of("100.00", EUR));
    settlement.reverseAllocation(ALLOCATION_ID);

    settlement.allocate(OTHER_ALLOCATION_ID, payment, invoice, Money.of("70.00", EUR));

    assertEquals(2, settlement.allocations().size());
    assertEquals(Money.of("70.00", EUR), settlement.allocatedAmount());
    assertEquals(Money.of("30.00", EUR), settlement.unappliedAmount(payment));
  }

  @Test
  void shouldRejectReversalOfUnknownAllocation() {
    ReceivableSettlement settlement =
        ReceivableSettlement.open(
            payment(PAYMENT_ID, TENANT_ID, CUSTOMER_ID, "100.00", EUR, false));

    assertThrows(IllegalArgumentException.class, () -> settlement.reverseAllocation(ALLOCATION_ID));
  }

  @Test
  void shouldFailClosedWhenAuthoritativePaymentShrinksBelowActiveAllocations() {
    Payment payment = payment(PAYMENT_ID, TENANT_ID, CUSTOMER_ID, "100.00", EUR, false);
    Invoice invoice = invoice(INVOICE_ID, TENANT_ID, CUSTOMER_ID, "100.00", "0.00", EUR, false);
    ReceivableSettlement settlement = ReceivableSettlement.open(payment);
    settlement.allocate(ALLOCATION_ID, payment, invoice, Money.of("80.00", EUR));
    Payment corrected = payment(PAYMENT_ID, TENANT_ID, CUSTOMER_ID, "70.00", EUR, false);

    assertThrows(IllegalStateException.class, () -> settlement.unappliedAmount(corrected));
  }

  @Test
  void shouldFailClosedWhenAuthoritativePaymentIsReversedWithActiveAllocations() {
    Payment payment = payment(PAYMENT_ID, TENANT_ID, CUSTOMER_ID, "100.00", EUR, false);
    Invoice invoice = invoice(INVOICE_ID, TENANT_ID, CUSTOMER_ID, "100.00", "0.00", EUR, false);
    ReceivableSettlement settlement = ReceivableSettlement.open(payment);
    settlement.allocate(ALLOCATION_ID, payment, invoice, Money.of("80.00", EUR));
    Payment reversed = payment(PAYMENT_ID, TENANT_ID, CUSTOMER_ID, "100.00", EUR, true);

    assertThrows(IllegalStateException.class, () -> settlement.unappliedAmount(reversed));
  }

  @Test
  void shouldReturnZeroUnappliedForReversedPaymentWhenNoActiveAllocationRemains() {
    Payment payment = payment(PAYMENT_ID, TENANT_ID, CUSTOMER_ID, "100.00", EUR, false);
    Invoice invoice = invoice(INVOICE_ID, TENANT_ID, CUSTOMER_ID, "100.00", "0.00", EUR, false);
    ReceivableSettlement settlement = ReceivableSettlement.open(payment);
    settlement.allocate(ALLOCATION_ID, payment, invoice, Money.of("80.00", EUR));
    settlement.reverseAllocation(ALLOCATION_ID);
    Payment reversed = payment(PAYMENT_ID, TENANT_ID, CUSTOMER_ID, "100.00", EUR, true);

    assertEquals(Money.zero(EUR), settlement.unappliedAmount(reversed));
  }

  @Test
  void shouldRejectCurrentPaymentCustomerOrCurrencyCorrectionsAgainstExistingSettlement() {
    Payment payment = payment(PAYMENT_ID, TENANT_ID, CUSTOMER_ID, "100.00", EUR, false);
    ReceivableSettlement settlement = ReceivableSettlement.open(payment);

    assertThrows(
        IllegalArgumentException.class,
        () ->
            settlement.unappliedAmount(
                payment(PAYMENT_ID, TENANT_ID, OTHER_CUSTOMER_ID, "100.00", EUR, false)));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            settlement.unappliedAmount(
                payment(PAYMENT_ID, TENANT_ID, CUSTOMER_ID, "100.00", USD, false)));
  }

  @Test
  void shouldReconstituteWithDefensiveImmutableAllocationSnapshot() {
    ReceivableAllocation active =
        ReceivableAllocation.active(ALLOCATION_ID, INVOICE_ID, Money.of("25.00", EUR));
    ReceivableAllocation reversed =
        ReceivableAllocation.active(OTHER_ALLOCATION_ID, OTHER_INVOICE_ID, Money.of("10.00", EUR))
            .reverse();
    ArrayList<ReceivableAllocation> source = new ArrayList<>(List.of(active, reversed));

    ReceivableSettlement settlement =
        ReceivableSettlement.reconstitute(
            SETTLEMENT_ID, TENANT_ID, CUSTOMER_ID, PAYMENT_ID, EUR, source);
    source.clear();

    assertEquals(SETTLEMENT_ID, settlement.id());
    assertEquals(List.of(active, reversed), settlement.allocations());
    assertEquals(Money.of("25.00", EUR), settlement.allocatedAmount());
    assertThrows(UnsupportedOperationException.class, () -> settlement.allocations().add(active));
  }

  @Test
  void shouldRejectInvalidReconstitutedSettlementFacts() {
    ReceivableAllocation eur =
        ReceivableAllocation.active(ALLOCATION_ID, INVOICE_ID, Money.of("10.00", EUR));
    ReceivableAllocation duplicate =
        ReceivableAllocation.active(ALLOCATION_ID, OTHER_INVOICE_ID, Money.of("5.00", EUR));
    ReceivableAllocation usd =
        ReceivableAllocation.active(OTHER_ALLOCATION_ID, INVOICE_ID, Money.of("5.00", USD));

    assertThrows(
        NullPointerException.class,
        () ->
            ReceivableSettlement.reconstitute(
                null, TENANT_ID, CUSTOMER_ID, PAYMENT_ID, EUR, List.of()));
    assertThrows(
        NullPointerException.class,
        () ->
            ReceivableSettlement.reconstitute(
                SETTLEMENT_ID, null, CUSTOMER_ID, PAYMENT_ID, EUR, List.of()));
    assertThrows(
        NullPointerException.class,
        () ->
            ReceivableSettlement.reconstitute(
                SETTLEMENT_ID, TENANT_ID, null, PAYMENT_ID, EUR, List.of()));
    assertThrows(
        NullPointerException.class,
        () ->
            ReceivableSettlement.reconstitute(
                SETTLEMENT_ID, TENANT_ID, CUSTOMER_ID, null, EUR, List.of()));
    assertThrows(
        NullPointerException.class,
        () ->
            ReceivableSettlement.reconstitute(
                SETTLEMENT_ID, TENANT_ID, CUSTOMER_ID, PAYMENT_ID, null, List.of()));
    assertThrows(
        NullPointerException.class,
        () ->
            ReceivableSettlement.reconstitute(
                SETTLEMENT_ID, TENANT_ID, CUSTOMER_ID, PAYMENT_ID, EUR, null));
    assertThrows(
        NullPointerException.class,
        () ->
            ReceivableSettlement.reconstitute(
                SETTLEMENT_ID,
                TENANT_ID,
                CUSTOMER_ID,
                PAYMENT_ID,
                EUR,
                java.util.Arrays.asList((ReceivableAllocation) null)));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            ReceivableSettlement.reconstitute(
                SETTLEMENT_ID, TENANT_ID, CUSTOMER_ID, PAYMENT_ID, EUR, List.of(eur, duplicate)));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            ReceivableSettlement.reconstitute(
                SETTLEMENT_ID, TENANT_ID, CUSTOMER_ID, PAYMENT_ID, EUR, List.of(usd)));
  }

  @Test
  void allocationValueShouldRejectMissingFactsAndNonPositiveAmount() {
    assertThrows(
        NullPointerException.class,
        () ->
            new ReceivableAllocation(
                null, INVOICE_ID, Money.of("1.00", EUR), ReceivableAllocationState.ACTIVE));
    assertThrows(
        NullPointerException.class,
        () ->
            new ReceivableAllocation(
                ALLOCATION_ID, null, Money.of("1.00", EUR), ReceivableAllocationState.ACTIVE));
    assertThrows(
        NullPointerException.class,
        () ->
            new ReceivableAllocation(
                ALLOCATION_ID, INVOICE_ID, null, ReceivableAllocationState.ACTIVE));
    assertThrows(
        NullPointerException.class,
        () -> new ReceivableAllocation(ALLOCATION_ID, INVOICE_ID, Money.of("1.00", EUR), null));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new ReceivableAllocation(
                ALLOCATION_ID, INVOICE_ID, Money.zero(EUR), ReceivableAllocationState.ACTIVE));
  }

  private static Payment payment(
      PaymentId id,
      OperationsTenantId tenantId,
      BusinessPartnerId customerId,
      String amount,
      CurrencyCode currency,
      boolean reversed) {
    return Payment.reconstitute(
        id, tenantId, customerId, Money.of(amount, currency), PAYMENT_DATE, reversed);
  }

  private static Invoice invoice(
      InvoiceId id,
      OperationsTenantId tenantId,
      BusinessPartnerId customerId,
      String originalAmount,
      String paidAmount,
      CurrencyCode currency,
      boolean cancelled) {
    return Invoice.reconstitute(
        id,
        tenantId,
        customerId,
        new InvoiceNumber("INV-" + id.value()),
        Money.of(originalAmount, currency),
        Money.of(paidAmount, currency),
        ISSUE_DATE,
        DUE_DATE,
        cancelled);
  }
}
