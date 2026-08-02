package io.github.khaledshawki.eoc.connectormanagement.application.model.datasource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Currency;
import java.util.Objects;

/** Normalized invoice data returned by a source adapter, not an owned business aggregate. */
public record SourceInvoiceRecord(
    SourceRecordMetadata metadata,
    SourceIdentity customerIdentity,
    String invoiceNumber,
    LocalDate issueDate,
    LocalDate dueDate,
    Currency currency,
    BigDecimal totalAmount,
    BigDecimal openAmount,
    String sourceStatus) {

  public static final int MAX_INVOICE_NUMBER_LENGTH = 100;
  public static final int MAX_SOURCE_STATUS_LENGTH = 63;

  public SourceInvoiceRecord {
    Objects.requireNonNull(metadata, "Invoice source metadata cannot be null");
    Objects.requireNonNull(customerIdentity, "Invoice customer identity cannot be null");
    if (!SourceEntity.INVOICE.equals(metadata.identity().entity())) {
      throw new IllegalArgumentException("Invoice source identity must use the invoice entity");
    }
    if (!SourceEntity.CUSTOMER.equals(customerIdentity.entity())) {
      throw new IllegalArgumentException("Invoice customer identity must use the customer entity");
    }
    invoiceNumber =
        SourceContractValidation.requiredText(
            invoiceNumber, "Invoice number", MAX_INVOICE_NUMBER_LENGTH);
    Objects.requireNonNull(issueDate, "Invoice issue date cannot be null");
    Objects.requireNonNull(dueDate, "Invoice due date cannot be null");
    Objects.requireNonNull(currency, "Invoice currency cannot be null");
    Objects.requireNonNull(totalAmount, "Invoice total amount cannot be null");
    Objects.requireNonNull(openAmount, "Invoice open amount cannot be null");
    sourceStatus =
        SourceContractValidation.requiredText(
            sourceStatus, "Invoice source status", MAX_SOURCE_STATUS_LENGTH);
  }
}
