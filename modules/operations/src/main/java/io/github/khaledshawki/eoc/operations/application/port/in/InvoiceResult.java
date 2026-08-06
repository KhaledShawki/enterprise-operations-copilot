package io.github.khaledshawki.eoc.operations.application.port.in;

import io.github.khaledshawki.eoc.operations.application.model.querying.InvoiceDueState;
import io.github.khaledshawki.eoc.operations.domain.model.BusinessPartnerId;
import io.github.khaledshawki.eoc.operations.domain.model.Invoice;
import io.github.khaledshawki.eoc.operations.domain.model.InvoiceId;
import io.github.khaledshawki.eoc.operations.domain.model.InvoiceNumber;
import io.github.khaledshawki.eoc.operations.domain.model.InvoiceStatus;
import io.github.khaledshawki.eoc.operations.domain.model.Money;
import io.github.khaledshawki.eoc.operations.domain.model.OperationsTenantId;
import java.time.LocalDate;
import java.util.Objects;

public record InvoiceResult(
    InvoiceId invoiceId,
    OperationsTenantId tenantId,
    BusinessPartnerId customerId,
    InvoiceNumber invoiceNumber,
    Money originalAmount,
    Money paidAmount,
    Money openAmount,
    LocalDate issueDate,
    LocalDate dueDate,
    LocalDate businessDate,
    InvoiceStatus status,
    InvoiceDueState dueState,
    boolean cancelled,
    boolean overdue) {

  public InvoiceResult {
    Objects.requireNonNull(invoiceId, "Invoice result id cannot be null");
    Objects.requireNonNull(tenantId, "Invoice result tenant id cannot be null");
    Objects.requireNonNull(customerId, "Invoice result customer id cannot be null");
    Objects.requireNonNull(invoiceNumber, "Invoice result number cannot be null");
    Objects.requireNonNull(originalAmount, "Invoice result original amount cannot be null");
    Objects.requireNonNull(paidAmount, "Invoice result paid amount cannot be null");
    Objects.requireNonNull(openAmount, "Invoice result open amount cannot be null");
    Objects.requireNonNull(issueDate, "Invoice result issue date cannot be null");
    Objects.requireNonNull(dueDate, "Invoice result due date cannot be null");
    Objects.requireNonNull(businessDate, "Invoice result business date cannot be null");
    Objects.requireNonNull(status, "Invoice result status cannot be null");
    Objects.requireNonNull(dueState, "Invoice result due state cannot be null");
    if (overdue != (dueState == InvoiceDueState.OVERDUE)) {
      throw new IllegalArgumentException("Invoice overdue flag must match its due state");
    }
  }

  public static InvoiceResult from(Invoice invoice, LocalDate businessDate) {
    Objects.requireNonNull(invoice, "Invoice cannot be null");
    InvoiceDueState dueState = InvoiceDueState.from(invoice, businessDate);
    return new InvoiceResult(
        invoice.id(),
        invoice.tenantId(),
        invoice.customerId(),
        invoice.invoiceNumber(),
        invoice.originalAmount(),
        invoice.paidAmount(),
        invoice.openAmount(),
        invoice.issueDate(),
        invoice.dueDate(),
        businessDate,
        invoice.status(),
        dueState,
        invoice.cancelled(),
        dueState == InvoiceDueState.OVERDUE);
  }
}
