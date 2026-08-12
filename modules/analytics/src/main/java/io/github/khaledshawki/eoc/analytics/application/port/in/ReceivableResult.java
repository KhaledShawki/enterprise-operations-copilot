package io.github.khaledshawki.eoc.analytics.application.port.in;

import io.github.khaledshawki.eoc.analytics.application.model.querying.ReceivableCustomerSummary;
import io.github.khaledshawki.eoc.analytics.application.model.querying.ReceivableSnapshot;
import io.github.khaledshawki.eoc.analytics.domain.model.AnalyticsMoney;
import io.github.khaledshawki.eoc.analytics.domain.model.InvoiceReceivableStatus;
import io.github.khaledshawki.eoc.analytics.domain.model.ProjectionCursor;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

public record ReceivableResult(
    UUID tenantId,
    UUID invoiceId,
    ReceivableCustomerSummary customer,
    String invoiceNumber,
    AnalyticsMoney originalAmount,
    AnalyticsMoney paidAmount,
    AnalyticsMoney outstandingAmount,
    LocalDate issueDate,
    LocalDate dueDate,
    LocalDate businessDate,
    InvoiceReceivableStatus status,
    boolean cancelled,
    boolean overdue,
    ProjectionCursor source) {

  public ReceivableResult {
    Objects.requireNonNull(tenantId, "Receivable result tenant id cannot be null");
    Objects.requireNonNull(invoiceId, "Receivable result invoice id cannot be null");
    Objects.requireNonNull(customer, "Receivable result customer cannot be null");
    Objects.requireNonNull(invoiceNumber, "Receivable result invoice number cannot be null");
    Objects.requireNonNull(originalAmount, "Receivable result original amount cannot be null");
    Objects.requireNonNull(paidAmount, "Receivable result paid amount cannot be null");
    Objects.requireNonNull(
        outstandingAmount, "Receivable result outstanding amount cannot be null");
    Objects.requireNonNull(issueDate, "Receivable result issue date cannot be null");
    Objects.requireNonNull(dueDate, "Receivable result due date cannot be null");
    Objects.requireNonNull(businessDate, "Receivable result business date cannot be null");
    Objects.requireNonNull(status, "Receivable result status cannot be null");
    Objects.requireNonNull(source, "Receivable result source cannot be null");
  }

  public static ReceivableResult from(ReceivableSnapshot snapshot, LocalDate businessDate) {
    Objects.requireNonNull(snapshot, "Receivable snapshot cannot be null");
    Objects.requireNonNull(businessDate, "Receivable business date cannot be null");
    var invoice = snapshot.invoice();
    return new ReceivableResult(
        invoice.tenantId().value(),
        invoice.invoiceId(),
        snapshot.customer(),
        invoice.invoiceNumber(),
        invoice.originalAmount(),
        invoice.paidAmount(),
        invoice.outstandingAmount(),
        invoice.issueDate(),
        invoice.dueDate(),
        businessDate,
        invoice.status(),
        invoice.cancelled(),
        invoice.isOverdueOn(businessDate),
        invoice.source());
  }
}
