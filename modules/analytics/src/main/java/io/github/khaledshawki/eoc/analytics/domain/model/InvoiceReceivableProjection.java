package io.github.khaledshawki.eoc.analytics.domain.model;

import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

public record InvoiceReceivableProjection(
    AnalyticsTenantId tenantId,
    UUID invoiceId,
    UUID customerId,
    String invoiceNumber,
    AnalyticsMoney originalAmount,
    AnalyticsMoney paidAmount,
    LocalDate issueDate,
    LocalDate dueDate,
    boolean cancelled,
    InvoiceReceivableStatus status,
    ProjectionCursor source) {

  public static final int MAX_INVOICE_NUMBER_LENGTH = 100;

  public InvoiceReceivableProjection {
    Objects.requireNonNull(tenantId, "Invoice receivable projection tenant id cannot be null");
    Objects.requireNonNull(invoiceId, "Invoice receivable projection invoice id cannot be null");
    Objects.requireNonNull(customerId, "Invoice receivable projection customer id cannot be null");
    invoiceNumber = requireInvoiceNumber(invoiceNumber);
    Objects.requireNonNull(
        originalAmount, "Invoice receivable projection original amount cannot be null");
    Objects.requireNonNull(paidAmount, "Invoice receivable projection paid amount cannot be null");
    Objects.requireNonNull(issueDate, "Invoice receivable projection issue date cannot be null");
    Objects.requireNonNull(dueDate, "Invoice receivable projection due date cannot be null");
    Objects.requireNonNull(status, "Invoice receivable projection status cannot be null");
    Objects.requireNonNull(source, "Invoice receivable projection source cannot be null");

    if (!originalAmount.currency().equals(paidAmount.currency())) {
      throw new IllegalArgumentException("Invoice receivable projection currencies must match");
    }
    if (originalAmount.isNegative()) {
      throw new IllegalArgumentException(
          "Invoice receivable projection original amount cannot be negative");
    }
    if (paidAmount.isNegative() || paidAmount.compareTo(originalAmount) > 0) {
      throw new IllegalArgumentException(
          "Invoice receivable projection paid amount is outside the valid range");
    }
    if (dueDate.isBefore(issueDate)) {
      throw new IllegalArgumentException(
          "Invoice receivable projection due date cannot precede issue date");
    }

    InvoiceReceivableStatus expectedStatus = expectedStatus(originalAmount, paidAmount, cancelled);
    if (status != expectedStatus) {
      throw new IllegalArgumentException(
          "Invoice receivable projection status does not match its canonical facts");
    }
  }

  public AnalyticsMoney outstandingAmount() {
    return originalAmount.subtract(paidAmount);
  }

  public boolean isOverdueOn(LocalDate date) {
    Objects.requireNonNull(date, "Overdue evaluation date cannot be null");
    return dueDate.isBefore(date)
        && (status == InvoiceReceivableStatus.OPEN
            || status == InvoiceReceivableStatus.PARTIALLY_PAID)
        && outstandingAmount().isPositive();
  }

  private static InvoiceReceivableStatus expectedStatus(
      AnalyticsMoney originalAmount, AnalyticsMoney paidAmount, boolean cancelled) {
    if (cancelled) {
      return InvoiceReceivableStatus.CANCELLED;
    }
    if (paidAmount.compareTo(originalAmount) == 0) {
      return InvoiceReceivableStatus.PAID;
    }
    if (paidAmount.isPositive()) {
      return InvoiceReceivableStatus.PARTIALLY_PAID;
    }
    return InvoiceReceivableStatus.OPEN;
  }

  private static String requireInvoiceNumber(String value) {
    Objects.requireNonNull(value, "Invoice receivable projection invoice number cannot be null");
    String normalized = value.strip();
    if (normalized.isEmpty()) {
      throw new IllegalArgumentException(
          "Invoice receivable projection invoice number cannot be blank");
    }
    if (normalized.length() > MAX_INVOICE_NUMBER_LENGTH) {
      throw new IllegalArgumentException(
          "Invoice receivable projection invoice number cannot exceed "
              + MAX_INVOICE_NUMBER_LENGTH
              + " characters");
    }
    return normalized;
  }
}
