package io.github.khaledshawki.eoc.analytics.application.port.in;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

public record ProjectInvoiceReceivableCommand(
    UUID eventId,
    UUID tenantId,
    UUID invoiceId,
    long aggregateVersion,
    Instant occurredAt,
    UUID customerId,
    String invoiceNumber,
    BigDecimal originalAmount,
    BigDecimal paidAmount,
    String currency,
    LocalDate issueDate,
    LocalDate dueDate,
    boolean cancelled,
    String status) {

  public ProjectInvoiceReceivableCommand {
    Objects.requireNonNull(eventId, "Invoice projection event id cannot be null");
    Objects.requireNonNull(tenantId, "Invoice projection tenant id cannot be null");
    Objects.requireNonNull(invoiceId, "Invoice projection invoice id cannot be null");
    Objects.requireNonNull(occurredAt, "Invoice projection occurredAt cannot be null");
    Objects.requireNonNull(customerId, "Invoice projection customer id cannot be null");
    Objects.requireNonNull(invoiceNumber, "Invoice projection invoice number cannot be null");
    Objects.requireNonNull(originalAmount, "Invoice projection original amount cannot be null");
    Objects.requireNonNull(paidAmount, "Invoice projection paid amount cannot be null");
    Objects.requireNonNull(currency, "Invoice projection currency cannot be null");
    Objects.requireNonNull(issueDate, "Invoice projection issue date cannot be null");
    Objects.requireNonNull(dueDate, "Invoice projection due date cannot be null");
    Objects.requireNonNull(status, "Invoice projection status cannot be null");
  }
}
