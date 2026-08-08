package io.github.khaledshawki.eoc.operations.domain.model;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Canonical imported customer-invoice snapshot.
 *
 * <p>{@code paidAmount} is authoritative settlement evidence from the Invoice source snapshot.
 * Local receivable allocations are modeled separately and must never mutate this source-owned fact.
 */
public final class Invoice {

  private final InvoiceId id;
  private final OperationsTenantId tenantId;
  private BusinessPartnerId customerId;
  private InvoiceNumber invoiceNumber;
  private Money originalAmount;
  private Money paidAmount;
  private LocalDate issueDate;
  private LocalDate dueDate;
  private boolean cancelled;

  private Invoice(
      InvoiceId id,
      OperationsTenantId tenantId,
      BusinessPartnerId customerId,
      InvoiceNumber invoiceNumber,
      Money originalAmount,
      Money paidAmount,
      LocalDate issueDate,
      LocalDate dueDate,
      boolean cancelled) {
    this.id = Objects.requireNonNull(id, "Invoice id cannot be null");
    this.tenantId = Objects.requireNonNull(tenantId, "Invoice tenant id cannot be null");
    InvoiceFacts facts =
        validateFacts(
            customerId, invoiceNumber, originalAmount, paidAmount, issueDate, dueDate, cancelled);
    replaceFacts(facts);
  }

  public static Invoice importCustomerInvoice(
      OperationsTenantId tenantId,
      BusinessPartnerId customerId,
      InvoiceNumber invoiceNumber,
      Money originalAmount,
      Money paidAmount,
      LocalDate issueDate,
      LocalDate dueDate,
      boolean cancelled) {
    return new Invoice(
        InvoiceId.generate(),
        tenantId,
        customerId,
        invoiceNumber,
        originalAmount,
        paidAmount,
        issueDate,
        dueDate,
        cancelled);
  }

  public static Invoice reconstitute(
      InvoiceId id,
      OperationsTenantId tenantId,
      BusinessPartnerId customerId,
      InvoiceNumber invoiceNumber,
      Money originalAmount,
      Money paidAmount,
      LocalDate issueDate,
      LocalDate dueDate,
      boolean cancelled) {
    return new Invoice(
        id,
        tenantId,
        customerId,
        invoiceNumber,
        originalAmount,
        paidAmount,
        issueDate,
        dueDate,
        cancelled);
  }

  public void synchronizeCustomerInvoice(
      BusinessPartnerId customerId,
      InvoiceNumber invoiceNumber,
      Money originalAmount,
      Money paidAmount,
      LocalDate issueDate,
      LocalDate dueDate,
      boolean cancelled) {
    InvoiceFacts facts =
        validateFacts(
            customerId, invoiceNumber, originalAmount, paidAmount, issueDate, dueDate, cancelled);
    replaceFacts(facts);
  }

  public Money remainingAmount() {
    return originalAmount.subtract(paidAmount);
  }

  public Money openAmount() {
    return cancelled ? Money.zero(originalAmount.currency()) : remainingAmount();
  }

  public InvoiceStatus status() {
    if (cancelled) {
      return InvoiceStatus.CANCELLED;
    }
    if (remainingAmount().isZero()) {
      return InvoiceStatus.PAID;
    }
    if (paidAmount.isPositive()) {
      return InvoiceStatus.PARTIALLY_PAID;
    }
    return InvoiceStatus.OPEN;
  }

  public boolean isOverdue(LocalDate businessDate) {
    Objects.requireNonNull(businessDate, "Business date cannot be null");
    return !cancelled && dueDate.isBefore(businessDate) && openAmount().isPositive();
  }

  public InvoiceId id() {
    return id;
  }

  public OperationsTenantId tenantId() {
    return tenantId;
  }

  public BusinessPartnerId customerId() {
    return customerId;
  }

  public InvoiceNumber invoiceNumber() {
    return invoiceNumber;
  }

  public Money originalAmount() {
    return originalAmount;
  }

  public Money paidAmount() {
    return paidAmount;
  }

  public LocalDate issueDate() {
    return issueDate;
  }

  public LocalDate dueDate() {
    return dueDate;
  }

  public boolean cancelled() {
    return cancelled;
  }

  private static InvoiceFacts validateFacts(
      BusinessPartnerId customerId,
      InvoiceNumber invoiceNumber,
      Money originalAmount,
      Money paidAmount,
      LocalDate issueDate,
      LocalDate dueDate,
      boolean cancelled) {
    Objects.requireNonNull(customerId, "Invoice customer id cannot be null");
    Objects.requireNonNull(invoiceNumber, "Invoice number cannot be null");
    Objects.requireNonNull(originalAmount, "Invoice original amount cannot be null");
    Objects.requireNonNull(paidAmount, "Invoice paid amount cannot be null");
    Objects.requireNonNull(issueDate, "Invoice issue date cannot be null");
    Objects.requireNonNull(dueDate, "Invoice due date cannot be null");

    if (!originalAmount.currency().equals(paidAmount.currency())) {
      throw new IllegalArgumentException("Invoice monetary amounts must use the same currency");
    }
    if (originalAmount.isNegative()) {
      throw new IllegalArgumentException("Invoice original amount cannot be negative");
    }
    if (paidAmount.isNegative()) {
      throw new IllegalArgumentException("Invoice paid amount cannot be negative");
    }
    if (paidAmount.compareTo(originalAmount) > 0) {
      throw new IllegalArgumentException("Invoice paid amount cannot exceed original amount");
    }
    if (dueDate.isBefore(issueDate)) {
      throw new IllegalArgumentException("Invoice due date cannot precede issue date");
    }

    return new InvoiceFacts(
        customerId, invoiceNumber, originalAmount, paidAmount, issueDate, dueDate, cancelled);
  }

  private void replaceFacts(InvoiceFacts facts) {
    customerId = facts.customerId();
    invoiceNumber = facts.invoiceNumber();
    originalAmount = facts.originalAmount();
    paidAmount = facts.paidAmount();
    issueDate = facts.issueDate();
    dueDate = facts.dueDate();
    cancelled = facts.cancelled();
  }

  private record InvoiceFacts(
      BusinessPartnerId customerId,
      InvoiceNumber invoiceNumber,
      Money originalAmount,
      Money paidAmount,
      LocalDate issueDate,
      LocalDate dueDate,
      boolean cancelled) {}
}
