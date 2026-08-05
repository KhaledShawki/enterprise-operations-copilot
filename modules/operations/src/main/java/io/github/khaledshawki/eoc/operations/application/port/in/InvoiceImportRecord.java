package io.github.khaledshawki.eoc.operations.application.port.in;

import io.github.khaledshawki.eoc.operations.domain.model.InvoiceNumber;
import io.github.khaledshawki.eoc.operations.domain.model.Money;
import io.github.khaledshawki.eoc.operations.domain.model.SourceRecordIdentity;
import io.github.khaledshawki.eoc.operations.domain.model.SourceRecordVersion;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

public record InvoiceImportRecord(
    SourceRecordIdentity sourceIdentity,
    SourceRecordVersion sourceVersion,
    Optional<Instant> sourceModifiedAt,
    SourceRecordIdentity customerSourceIdentity,
    InvoiceNumber invoiceNumber,
    Money originalAmount,
    Money paidAmount,
    LocalDate issueDate,
    LocalDate dueDate,
    boolean cancelled) {

  public InvoiceImportRecord {
    Objects.requireNonNull(sourceIdentity, "Invoice source identity cannot be null");
    Objects.requireNonNull(sourceVersion, "Invoice source version cannot be null");
    Objects.requireNonNull(
        sourceModifiedAt, "Invoice source modification timestamp cannot be null");
    Objects.requireNonNull(
        customerSourceIdentity, "Invoice customer source identity cannot be null");
    Objects.requireNonNull(invoiceNumber, "Invoice number cannot be null");
    Objects.requireNonNull(originalAmount, "Invoice original amount cannot be null");
    Objects.requireNonNull(paidAmount, "Invoice paid amount cannot be null");
    Objects.requireNonNull(issueDate, "Invoice issue date cannot be null");
    Objects.requireNonNull(dueDate, "Invoice due date cannot be null");
  }
}
