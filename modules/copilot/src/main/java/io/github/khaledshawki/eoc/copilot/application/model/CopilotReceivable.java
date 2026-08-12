package io.github.khaledshawki.eoc.copilot.application.model;

import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

public record CopilotReceivable(
    UUID tenantId,
    UUID invoiceId,
    CopilotCustomer customer,
    String invoiceNumber,
    CopilotMoney originalAmount,
    CopilotMoney paidAmount,
    CopilotMoney outstandingAmount,
    LocalDate issueDate,
    LocalDate dueDate,
    LocalDate businessDate,
    ReceivableStatus status,
    boolean cancelled,
    boolean overdue,
    CopilotEvidence evidence) {
  public CopilotReceivable {
    Objects.requireNonNull(tenantId, "Copilot receivable tenant id cannot be null");
    Objects.requireNonNull(invoiceId, "Copilot receivable invoice id cannot be null");
    Objects.requireNonNull(customer, "Copilot receivable customer cannot be null");
    Objects.requireNonNull(invoiceNumber, "Copilot receivable invoice number cannot be null");
    invoiceNumber = invoiceNumber.strip();
    if (invoiceNumber.isEmpty()) {
      throw new IllegalArgumentException("Copilot receivable invoice number cannot be blank");
    }
    Objects.requireNonNull(originalAmount, "Copilot receivable original amount cannot be null");
    Objects.requireNonNull(paidAmount, "Copilot receivable paid amount cannot be null");
    Objects.requireNonNull(
        outstandingAmount, "Copilot receivable outstanding amount cannot be null");
    if (!originalAmount.currency().equals(paidAmount.currency())
        || !originalAmount.currency().equals(outstandingAmount.currency())) {
      throw new IllegalArgumentException("Copilot receivable currencies must match");
    }
    if (originalAmount.amount().subtract(paidAmount.amount()).compareTo(outstandingAmount.amount())
        != 0) {
      throw new IllegalArgumentException("Copilot receivable outstanding amount is inconsistent");
    }
    Objects.requireNonNull(issueDate, "Copilot receivable issue date cannot be null");
    Objects.requireNonNull(dueDate, "Copilot receivable due date cannot be null");
    if (dueDate.isBefore(issueDate)) {
      throw new IllegalArgumentException("Copilot receivable due date cannot precede issue date");
    }
    Objects.requireNonNull(businessDate, "Copilot receivable business date cannot be null");
    Objects.requireNonNull(status, "Copilot receivable status cannot be null");
    ReceivableStatus expectedStatus;
    if (cancelled) {
      expectedStatus = ReceivableStatus.CANCELLED;
    } else if (paidAmount.amount().compareTo(originalAmount.amount()) == 0) {
      expectedStatus = ReceivableStatus.PAID;
    } else if (paidAmount.amount().signum() > 0) {
      expectedStatus = ReceivableStatus.PARTIALLY_PAID;
    } else {
      expectedStatus = ReceivableStatus.OPEN;
    }
    if (status != expectedStatus) {
      throw new IllegalArgumentException(
          "Copilot receivable status is inconsistent with its balances");
    }
    boolean expectedOverdue =
        dueDate.isBefore(businessDate)
            && (status == ReceivableStatus.OPEN || status == ReceivableStatus.PARTIALLY_PAID)
            && outstandingAmount.amount().signum() > 0;
    if (overdue != expectedOverdue) {
      throw new IllegalArgumentException("Copilot receivable overdue state is inconsistent");
    }
    Objects.requireNonNull(evidence, "Copilot receivable evidence cannot be null");
  }
}
