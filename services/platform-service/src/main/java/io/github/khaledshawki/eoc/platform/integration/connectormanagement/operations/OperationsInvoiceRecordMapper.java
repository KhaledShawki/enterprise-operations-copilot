package io.github.khaledshawki.eoc.platform.integration.connectormanagement.operations;

import io.github.khaledshawki.eoc.connectormanagement.application.model.datasource.SourceIdentity;
import io.github.khaledshawki.eoc.connectormanagement.application.model.datasource.SourceInvoiceRecord;
import io.github.khaledshawki.eoc.operations.application.port.in.InvoiceImportRecord;
import io.github.khaledshawki.eoc.operations.domain.model.CurrencyCode;
import io.github.khaledshawki.eoc.operations.domain.model.InvoiceNumber;
import io.github.khaledshawki.eoc.operations.domain.model.Money;
import io.github.khaledshawki.eoc.operations.domain.model.SourceRecordIdentity;
import io.github.khaledshawki.eoc.operations.domain.model.SourceRecordVersion;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/** Translates normalized connector Invoice facts into the Operations-owned import contract. */
final class OperationsInvoiceRecordMapper {

  private static final Set<String> ACTIVE_STATUSES =
      Set.of("OPEN", "UNPAID", "PARTIAL", "PARTIALLY_PAID", "PAID", "OVERDUE", "CLOSED");
  private static final Set<String> CANCELLED_STATUSES =
      Set.of("CANCELLED", "CANCELED", "VOID", "VOIDED");

  private OperationsInvoiceRecordMapper() {}

  static InvoiceImportRecord toOperationsRecord(SourceInvoiceRecord record) {
    Objects.requireNonNull(record, "Source invoice record cannot be null");
    CurrencyCode currency = CurrencyCode.of(record.currency().getCurrencyCode());
    Money originalAmount = new Money(record.totalAmount(), currency);
    Money openAmount = new Money(record.openAmount(), currency);
    validateAmounts(originalAmount, openAmount);

    return new InvoiceImportRecord(
        toOperationsIdentity(record.metadata().identity()),
        new SourceRecordVersion(record.metadata().modificationVersion().value()),
        record.metadata().sourceModifiedAt(),
        toOperationsIdentity(record.customerIdentity()),
        new InvoiceNumber(record.invoiceNumber()),
        originalAmount,
        originalAmount.subtract(openAmount),
        record.issueDate(),
        record.dueDate(),
        cancelled(record.sourceStatus()));
  }

  private static void validateAmounts(Money originalAmount, Money openAmount) {
    if (originalAmount.isNegative()) {
      throw new IllegalArgumentException("Invoice total amount cannot be negative");
    }
    if (openAmount.isNegative()) {
      throw new IllegalArgumentException("Invoice open amount cannot be negative");
    }
    if (openAmount.compareTo(originalAmount) > 0) {
      throw new IllegalArgumentException("Invoice open amount cannot exceed total amount");
    }
  }

  private static boolean cancelled(String sourceStatus) {
    Objects.requireNonNull(sourceStatus, "Invoice source status cannot be null");
    String normalized = sourceStatus.strip().toUpperCase(Locale.ROOT).replaceAll("[\\s-]+", "_");
    if (CANCELLED_STATUSES.contains(normalized)) {
      return true;
    }
    if (ACTIVE_STATUSES.contains(normalized)) {
      return false;
    }
    throw new IllegalArgumentException("Unsupported invoice source status: " + normalized);
  }

  private static SourceRecordIdentity toOperationsIdentity(SourceIdentity identity) {
    Objects.requireNonNull(identity, "Source identity cannot be null");
    return switch (identity.kind()) {
      case SOURCE_RECORD_ID -> SourceRecordIdentity.sourceRecordId(identity.value());
      case CANONICAL_RECORD_HASH -> SourceRecordIdentity.canonicalRecordHash(identity.value());
    };
  }
}
