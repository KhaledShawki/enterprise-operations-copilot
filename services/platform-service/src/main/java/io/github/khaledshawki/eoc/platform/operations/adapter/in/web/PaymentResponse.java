package io.github.khaledshawki.eoc.platform.operations.adapter.in.web;

import io.github.khaledshawki.eoc.operations.application.port.in.PaymentResult;
import io.github.khaledshawki.eoc.operations.domain.model.PaymentStatus;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

public record PaymentResponse(
    UUID id,
    UUID tenantId,
    UUID customerId,
    MoneyResponse amount,
    MoneyResponse effectiveAmount,
    LocalDate paymentDate,
    PaymentStatus status,
    boolean reversed) {

  public PaymentResponse {
    Objects.requireNonNull(id, "Payment response id cannot be null");
    Objects.requireNonNull(tenantId, "Payment response tenant id cannot be null");
    Objects.requireNonNull(customerId, "Payment response customer id cannot be null");
    Objects.requireNonNull(amount, "Payment response amount cannot be null");
    Objects.requireNonNull(effectiveAmount, "Payment response effective amount cannot be null");
    Objects.requireNonNull(paymentDate, "Payment response date cannot be null");
    Objects.requireNonNull(status, "Payment response status cannot be null");
  }

  static PaymentResponse from(PaymentResult result) {
    Objects.requireNonNull(result, "Payment result cannot be null");
    return new PaymentResponse(
        result.paymentId().value(),
        result.tenantId().value(),
        result.customerId().value(),
        MoneyResponse.from(result.amount()),
        MoneyResponse.from(result.effectiveAmount()),
        result.paymentDate(),
        result.status(),
        result.reversed());
  }
}
