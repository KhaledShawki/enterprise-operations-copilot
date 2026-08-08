package io.github.khaledshawki.eoc.operations.application.port.in;

import io.github.khaledshawki.eoc.operations.domain.model.BusinessPartnerId;
import io.github.khaledshawki.eoc.operations.domain.model.Money;
import io.github.khaledshawki.eoc.operations.domain.model.OperationsTenantId;
import io.github.khaledshawki.eoc.operations.domain.model.Payment;
import io.github.khaledshawki.eoc.operations.domain.model.PaymentId;
import io.github.khaledshawki.eoc.operations.domain.model.PaymentStatus;
import java.time.LocalDate;
import java.util.Objects;

public record PaymentResult(
    PaymentId paymentId,
    OperationsTenantId tenantId,
    BusinessPartnerId customerId,
    Money amount,
    Money effectiveAmount,
    LocalDate paymentDate,
    PaymentStatus status,
    boolean reversed) {

  public PaymentResult {
    Objects.requireNonNull(paymentId, "Payment result id cannot be null");
    Objects.requireNonNull(tenantId, "Payment result tenant id cannot be null");
    Objects.requireNonNull(customerId, "Payment result customer id cannot be null");
    Objects.requireNonNull(amount, "Payment result amount cannot be null");
    Objects.requireNonNull(effectiveAmount, "Payment result effective amount cannot be null");
    Objects.requireNonNull(paymentDate, "Payment result payment date cannot be null");
    Objects.requireNonNull(status, "Payment result status cannot be null");
    PaymentStatus expectedStatus = reversed ? PaymentStatus.REVERSED : PaymentStatus.RECORDED;
    if (status != expectedStatus) {
      throw new IllegalArgumentException("Payment result status must match its reversal state");
    }
    Money expectedEffectiveAmount = reversed ? Money.zero(amount.currency()) : amount;
    if (!effectiveAmount.equals(expectedEffectiveAmount)) {
      throw new IllegalArgumentException(
          "Payment result effective amount must match its reversal state");
    }
  }

  public static PaymentResult from(Payment payment) {
    Objects.requireNonNull(payment, "Payment cannot be null");
    return new PaymentResult(
        payment.id(),
        payment.tenantId(),
        payment.customerId(),
        payment.amount(),
        payment.effectiveAmount(),
        payment.paymentDate(),
        payment.status(),
        payment.reversed());
  }
}
