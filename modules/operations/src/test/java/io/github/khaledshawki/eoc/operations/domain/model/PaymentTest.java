package io.github.khaledshawki.eoc.operations.domain.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PaymentTest {

  private static final OperationsTenantId TENANT_ID =
      OperationsTenantId.of(UUID.fromString("00000000-0000-0000-0000-000000000001"));
  private static final BusinessPartnerId CUSTOMER_ID =
      BusinessPartnerId.of(UUID.fromString("00000000-0000-0000-0000-000000000101"));
  private static final BusinessPartnerId OTHER_CUSTOMER_ID =
      BusinessPartnerId.of(UUID.fromString("00000000-0000-0000-0000-000000000102"));
  private static final CurrencyCode EUR = CurrencyCode.of("EUR");
  private static final CurrencyCode USD = CurrencyCode.of("USD");
  private static final LocalDate PAYMENT_DATE = LocalDate.of(2026, 8, 6);

  @Test
  void shouldImportRecordedCustomerPaymentWithGeneratedIdentity() {
    Payment payment = payment("100.00", EUR, false);
    Payment another = payment("100.00", EUR, false);

    assertNotEquals(payment.id(), another.id());
    assertEquals(TENANT_ID, payment.tenantId());
    assertEquals(CUSTOMER_ID, payment.customerId());
    assertEquals(Money.of("100.00", EUR), payment.amount());
    assertEquals(PAYMENT_DATE, payment.paymentDate());
    assertFalse(payment.reversed());
    assertEquals(PaymentStatus.RECORDED, payment.status());
    assertEquals(Money.of("100.00", EUR), payment.effectiveAmount());
  }

  @Test
  void shouldReconstitutePaymentWithExistingIdentity() {
    PaymentId id = PaymentId.of(UUID.fromString("00000000-0000-0000-0000-000000000501"));

    Payment payment =
        Payment.reconstitute(
            id, TENANT_ID, CUSTOMER_ID, Money.of("100.00", EUR), PAYMENT_DATE, true);

    assertEquals(id, payment.id());
    assertTrue(payment.reversed());
    assertEquals(PaymentStatus.REVERSED, payment.status());
    assertEquals(Money.zero(EUR), payment.effectiveAmount());
  }

  @Test
  void shouldKeepOriginalAmountWhileReversalRemovesItsEffectiveValue() {
    Payment payment = payment("125.50", EUR, true);

    assertEquals(Money.of("125.50", EUR), payment.amount());
    assertEquals(Money.zero(EUR), payment.effectiveAmount());
    assertEquals(PaymentStatus.REVERSED, payment.status());
  }

  @Test
  void shouldRejectMissingPaymentIdentityWhenReconstituting() {
    assertThrows(
        NullPointerException.class,
        () ->
            Payment.reconstitute(
                null, TENANT_ID, CUSTOMER_ID, Money.of("100.00", EUR), PAYMENT_DATE, false));
  }

  @Test
  void shouldRejectMissingPaymentFacts() {
    Money amount = Money.of("100.00", EUR);

    assertThrows(
        NullPointerException.class,
        () -> Payment.importCustomerPayment(null, CUSTOMER_ID, amount, PAYMENT_DATE, false));
    assertThrows(
        NullPointerException.class,
        () -> Payment.importCustomerPayment(TENANT_ID, null, amount, PAYMENT_DATE, false));
    assertThrows(
        NullPointerException.class,
        () -> Payment.importCustomerPayment(TENANT_ID, CUSTOMER_ID, null, PAYMENT_DATE, false));
    assertThrows(
        NullPointerException.class,
        () -> Payment.importCustomerPayment(TENANT_ID, CUSTOMER_ID, amount, null, false));
  }

  @Test
  void shouldRejectZeroAndNegativeAmounts() {
    assertThrows(IllegalArgumentException.class, () -> payment("0.00", EUR, false));
    assertThrows(IllegalArgumentException.class, () -> payment("-0.01", EUR, false));
  }

  @Test
  void shouldSynchronizeCompleteAuthoritativeSnapshot() {
    Payment payment = payment("100.00", EUR, false);
    PaymentId id = payment.id();

    payment.synchronizeCustomerPayment(
        OTHER_CUSTOMER_ID, Money.of("150.00", USD), PAYMENT_DATE.plusDays(1), true);

    assertEquals(id, payment.id());
    assertEquals(TENANT_ID, payment.tenantId());
    assertEquals(OTHER_CUSTOMER_ID, payment.customerId());
    assertEquals(Money.of("150.00", USD), payment.amount());
    assertEquals(PAYMENT_DATE.plusDays(1), payment.paymentDate());
    assertTrue(payment.reversed());
    assertEquals(PaymentStatus.REVERSED, payment.status());
    assertEquals(Money.zero(USD), payment.effectiveAmount());
  }

  @Test
  void shouldLeaveAggregateUnchangedWhenSynchronizationIsInvalid() {
    Payment payment = payment("100.00", EUR, false);

    assertThrows(
        IllegalArgumentException.class,
        () ->
            payment.synchronizeCustomerPayment(
                OTHER_CUSTOMER_ID, Money.zero(USD), PAYMENT_DATE.plusDays(1), true));

    assertEquals(CUSTOMER_ID, payment.customerId());
    assertEquals(Money.of("100.00", EUR), payment.amount());
    assertEquals(PAYMENT_DATE, payment.paymentDate());
    assertFalse(payment.reversed());
    assertEquals(PaymentStatus.RECORDED, payment.status());
  }

  @Test
  void shouldReverseAndReopenFromAuthoritativeSnapshots() {
    Payment payment = payment("100.00", EUR, false);

    payment.synchronizeCustomerPayment(CUSTOMER_ID, Money.of("100.00", EUR), PAYMENT_DATE, true);

    assertTrue(payment.reversed());
    assertEquals(PaymentStatus.REVERSED, payment.status());
    assertEquals(Money.zero(EUR), payment.effectiveAmount());

    payment.synchronizeCustomerPayment(CUSTOMER_ID, Money.of("100.00", EUR), PAYMENT_DATE, false);

    assertFalse(payment.reversed());
    assertEquals(PaymentStatus.RECORDED, payment.status());
    assertEquals(Money.of("100.00", EUR), payment.effectiveAmount());
  }

  private static Payment payment(String amount, CurrencyCode currency, boolean reversed) {
    return Payment.importCustomerPayment(
        TENANT_ID, CUSTOMER_ID, Money.of(amount, currency), PAYMENT_DATE, reversed);
  }
}
