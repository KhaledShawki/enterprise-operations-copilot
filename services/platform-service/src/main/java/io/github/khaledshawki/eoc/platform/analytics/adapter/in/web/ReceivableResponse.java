package io.github.khaledshawki.eoc.platform.analytics.adapter.in.web;

import io.github.khaledshawki.eoc.analytics.application.port.in.ReceivableResult;
import io.github.khaledshawki.eoc.analytics.domain.model.InvoiceReceivableStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

public record ReceivableResponse(
    UUID id,
    UUID tenantId,
    CustomerResponse customer,
    String invoiceNumber,
    MoneyResponse originalAmount,
    MoneyResponse paidAmount,
    MoneyResponse outstandingAmount,
    LocalDate issueDate,
    LocalDate dueDate,
    LocalDate businessDate,
    InvoiceReceivableStatus status,
    boolean cancelled,
    boolean overdue,
    SourceResponse source) {

  public ReceivableResponse {
    Objects.requireNonNull(id, "Receivable response id cannot be null");
    Objects.requireNonNull(tenantId, "Receivable response tenant id cannot be null");
    Objects.requireNonNull(customer, "Receivable response customer cannot be null");
    Objects.requireNonNull(invoiceNumber, "Receivable response invoice number cannot be null");
    Objects.requireNonNull(originalAmount, "Receivable response original amount cannot be null");
    Objects.requireNonNull(paidAmount, "Receivable response paid amount cannot be null");
    Objects.requireNonNull(
        outstandingAmount, "Receivable response outstanding amount cannot be null");
    Objects.requireNonNull(issueDate, "Receivable response issue date cannot be null");
    Objects.requireNonNull(dueDate, "Receivable response due date cannot be null");
    Objects.requireNonNull(businessDate, "Receivable response business date cannot be null");
    Objects.requireNonNull(status, "Receivable response status cannot be null");
    Objects.requireNonNull(source, "Receivable response source cannot be null");
  }

  static ReceivableResponse from(ReceivableResult result) {
    Objects.requireNonNull(result, "Receivable result cannot be null");
    return new ReceivableResponse(
        result.invoiceId(),
        result.tenantId(),
        CustomerResponse.from(result),
        result.invoiceNumber(),
        MoneyResponse.from(result.originalAmount()),
        MoneyResponse.from(result.paidAmount()),
        MoneyResponse.from(result.outstandingAmount()),
        result.issueDate(),
        result.dueDate(),
        result.businessDate(),
        result.status(),
        result.cancelled(),
        result.overdue(),
        new SourceResponse(
            result.source().eventId(),
            result.source().aggregateVersion(),
            result.source().occurredAt()));
  }

  public record CustomerResponse(
      UUID id, boolean projected, String partnerNumber, String displayName) {

    static CustomerResponse from(ReceivableResult result) {
      var customer = result.customer();
      return new CustomerResponse(
          customer.customerId(),
          customer.projected(),
          customer.partnerNumber().orElse(null),
          customer.displayName().orElse(null));
    }
  }

  public record MoneyResponse(BigDecimal amount, String currency) {

    public MoneyResponse {
      Objects.requireNonNull(amount, "Receivable money amount cannot be null");
      Objects.requireNonNull(currency, "Receivable money currency cannot be null");
    }

    static MoneyResponse from(
        io.github.khaledshawki.eoc.analytics.domain.model.AnalyticsMoney money) {
      return new MoneyResponse(money.amount(), money.currency().value());
    }
  }

  public record SourceResponse(UUID eventId, long aggregateVersion, Instant occurredAt) {

    public SourceResponse {
      Objects.requireNonNull(eventId, "Receivable source event id cannot be null");
      if (aggregateVersion < 1) {
        throw new IllegalArgumentException("Receivable source aggregate version must be positive");
      }
      Objects.requireNonNull(occurredAt, "Receivable source occurrence time cannot be null");
    }
  }
}
