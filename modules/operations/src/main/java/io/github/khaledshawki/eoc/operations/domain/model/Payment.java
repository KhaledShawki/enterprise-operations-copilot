package io.github.khaledshawki.eoc.operations.domain.model;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Canonical customer-payment fact.
 *
 * <p>Invoice allocation is intentionally not modeled here. Applied and unapplied amounts require a
 * separate, explicitly designed settlement boundary so this aggregate does not guess allocation,
 * overpayment, or concurrency semantics.
 */
public final class Payment {

  private final PaymentId id;
  private final OperationsTenantId tenantId;
  private BusinessPartnerId customerId;
  private Money amount;
  private LocalDate paymentDate;
  private boolean reversed;

  private Payment(
      PaymentId id,
      OperationsTenantId tenantId,
      BusinessPartnerId customerId,
      Money amount,
      LocalDate paymentDate,
      boolean reversed) {
    this.id = Objects.requireNonNull(id, "Payment id cannot be null");
    this.tenantId = Objects.requireNonNull(tenantId, "Payment tenant id cannot be null");
    replaceFacts(validateFacts(customerId, amount, paymentDate, reversed));
  }

  public static Payment importCustomerPayment(
      OperationsTenantId tenantId,
      BusinessPartnerId customerId,
      Money amount,
      LocalDate paymentDate,
      boolean reversed) {
    return new Payment(PaymentId.generate(), tenantId, customerId, amount, paymentDate, reversed);
  }

  public static Payment reconstitute(
      PaymentId id,
      OperationsTenantId tenantId,
      BusinessPartnerId customerId,
      Money amount,
      LocalDate paymentDate,
      boolean reversed) {
    return new Payment(id, tenantId, customerId, amount, paymentDate, reversed);
  }

  public void synchronizeCustomerPayment(
      BusinessPartnerId customerId, Money amount, LocalDate paymentDate, boolean reversed) {
    replaceFacts(validateFacts(customerId, amount, paymentDate, reversed));
  }

  public PaymentStatus status() {
    return reversed ? PaymentStatus.REVERSED : PaymentStatus.RECORDED;
  }

  public Money effectiveAmount() {
    return reversed ? Money.zero(amount.currency()) : amount;
  }

  public PaymentId id() {
    return id;
  }

  public OperationsTenantId tenantId() {
    return tenantId;
  }

  public BusinessPartnerId customerId() {
    return customerId;
  }

  public Money amount() {
    return amount;
  }

  public LocalDate paymentDate() {
    return paymentDate;
  }

  public boolean reversed() {
    return reversed;
  }

  private static PaymentFacts validateFacts(
      BusinessPartnerId customerId, Money amount, LocalDate paymentDate, boolean reversed) {
    Objects.requireNonNull(customerId, "Payment customer id cannot be null");
    Objects.requireNonNull(amount, "Payment amount cannot be null");
    Objects.requireNonNull(paymentDate, "Payment date cannot be null");
    if (!amount.isPositive()) {
      throw new IllegalArgumentException("Payment amount must be positive");
    }
    return new PaymentFacts(customerId, amount, paymentDate, reversed);
  }

  private void replaceFacts(PaymentFacts facts) {
    customerId = facts.customerId();
    amount = facts.amount();
    paymentDate = facts.paymentDate();
    reversed = facts.reversed();
  }

  private record PaymentFacts(
      BusinessPartnerId customerId, Money amount, LocalDate paymentDate, boolean reversed) {}
}
