package io.github.khaledshawki.eoc.operations.application.model.event;

import io.github.khaledshawki.eoc.operations.domain.model.InvoiceNumber;
import io.github.khaledshawki.eoc.operations.domain.model.InvoiceStatus;
import io.github.khaledshawki.eoc.operations.domain.model.Money;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

public record InvoiceSynchronizedPayload(
    UUID invoiceId,
    UUID customerId,
    String invoiceNumber,
    OperationsMoneyPayload originalAmount,
    OperationsMoneyPayload paidAmount,
    LocalDate issueDate,
    LocalDate dueDate,
    boolean cancelled,
    String status,
    SourceRecordEvidence source)
    implements OperationsIntegrationEventPayload {

  public InvoiceSynchronizedPayload {
    Objects.requireNonNull(invoiceId, "Event invoice id cannot be null");
    Objects.requireNonNull(customerId, "Event invoice customer id cannot be null");
    invoiceNumber = new InvoiceNumber(invoiceNumber).value();
    Objects.requireNonNull(originalAmount, "Event invoice original amount cannot be null");
    Objects.requireNonNull(paidAmount, "Event invoice paid amount cannot be null");
    Objects.requireNonNull(issueDate, "Event invoice issue date cannot be null");
    Objects.requireNonNull(dueDate, "Event invoice due date cannot be null");
    Objects.requireNonNull(status, "Event invoice status cannot be null");
    Objects.requireNonNull(source, "Event invoice source cannot be null");

    Money original = originalAmount.toMoney();
    Money paid = paidAmount.toMoney();
    if (!original.currency().equals(paid.currency())) {
      throw new IllegalArgumentException("Event invoice amounts must use the same currency");
    }
    if (original.isNegative()) {
      throw new IllegalArgumentException("Event invoice original amount cannot be negative");
    }
    if (paid.isNegative() || paid.compareTo(original) > 0) {
      throw new IllegalArgumentException("Event invoice paid amount is outside the valid range");
    }
    if (dueDate.isBefore(issueDate)) {
      throw new IllegalArgumentException("Event invoice due date cannot precede issue date");
    }

    InvoiceStatus expectedStatus = expectedStatus(original, paid, cancelled);
    if (!expectedStatus.name().equals(status)) {
      throw new IllegalArgumentException("Event invoice status does not match its canonical facts");
    }
  }

  @Override
  public UUID aggregateId() {
    return invoiceId;
  }

  private static InvoiceStatus expectedStatus(Money original, Money paid, boolean cancelled) {
    if (cancelled) {
      return InvoiceStatus.CANCELLED;
    }
    if (paid.compareTo(original) == 0) {
      return InvoiceStatus.PAID;
    }
    if (paid.isPositive()) {
      return InvoiceStatus.PARTIALLY_PAID;
    }
    return InvoiceStatus.OPEN;
  }
}
