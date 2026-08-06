package io.github.khaledshawki.eoc.platform.operations.adapter.in.web;

import io.github.khaledshawki.eoc.operations.application.model.querying.InvoiceDueState;
import io.github.khaledshawki.eoc.operations.application.port.in.InvoiceResult;
import io.github.khaledshawki.eoc.operations.domain.model.InvoiceStatus;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

public record InvoiceResponse(
    UUID id,
    UUID tenantId,
    UUID customerId,
    String invoiceNumber,
    MoneyResponse originalAmount,
    MoneyResponse paidAmount,
    MoneyResponse openAmount,
    LocalDate issueDate,
    LocalDate dueDate,
    LocalDate businessDate,
    InvoiceStatus status,
    InvoiceDueState dueState,
    boolean cancelled,
    boolean overdue) {

  public InvoiceResponse {
    Objects.requireNonNull(id, "Invoice response id cannot be null");
    Objects.requireNonNull(tenantId, "Invoice response tenant id cannot be null");
    Objects.requireNonNull(customerId, "Invoice response customer id cannot be null");
    Objects.requireNonNull(invoiceNumber, "Invoice response number cannot be null");
    Objects.requireNonNull(originalAmount, "Invoice response original amount cannot be null");
    Objects.requireNonNull(paidAmount, "Invoice response paid amount cannot be null");
    Objects.requireNonNull(openAmount, "Invoice response open amount cannot be null");
    Objects.requireNonNull(issueDate, "Invoice response issue date cannot be null");
    Objects.requireNonNull(dueDate, "Invoice response due date cannot be null");
    Objects.requireNonNull(businessDate, "Invoice response business date cannot be null");
    Objects.requireNonNull(status, "Invoice response status cannot be null");
    Objects.requireNonNull(dueState, "Invoice response due state cannot be null");
  }

  static InvoiceResponse from(InvoiceResult result) {
    Objects.requireNonNull(result, "Invoice result cannot be null");
    return new InvoiceResponse(
        result.invoiceId().value(),
        result.tenantId().value(),
        result.customerId().value(),
        result.invoiceNumber().value(),
        MoneyResponse.from(result.originalAmount()),
        MoneyResponse.from(result.paidAmount()),
        MoneyResponse.from(result.openAmount()),
        result.issueDate(),
        result.dueDate(),
        result.businessDate(),
        result.status(),
        result.dueState(),
        result.cancelled(),
        result.overdue());
  }
}
