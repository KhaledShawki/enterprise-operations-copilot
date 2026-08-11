package io.github.khaledshawki.eoc.operations.application.model.event;

import io.github.khaledshawki.eoc.operations.domain.model.PaymentStatus;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

public record PaymentSynchronizedPayload(
    UUID paymentId,
    UUID customerId,
    OperationsMoneyPayload amount,
    LocalDate paymentDate,
    boolean reversed,
    String status,
    SourceRecordEvidence source)
    implements OperationsIntegrationEventPayload {

  public PaymentSynchronizedPayload {
    Objects.requireNonNull(paymentId, "Event payment id cannot be null");
    Objects.requireNonNull(customerId, "Event payment customer id cannot be null");
    Objects.requireNonNull(amount, "Event payment amount cannot be null");
    Objects.requireNonNull(paymentDate, "Event payment date cannot be null");
    Objects.requireNonNull(status, "Event payment status cannot be null");
    Objects.requireNonNull(source, "Event payment source cannot be null");
    if (!amount.toMoney().isPositive()) {
      throw new IllegalArgumentException("Event payment amount must be positive");
    }
    PaymentStatus expectedStatus = reversed ? PaymentStatus.REVERSED : PaymentStatus.RECORDED;
    if (!expectedStatus.name().equals(status)) {
      throw new IllegalArgumentException("Event payment status does not match its canonical facts");
    }
  }

  @Override
  public UUID aggregateId() {
    return paymentId;
  }
}
