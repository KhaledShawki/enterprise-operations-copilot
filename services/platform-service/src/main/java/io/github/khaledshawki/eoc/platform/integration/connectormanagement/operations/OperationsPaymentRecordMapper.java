package io.github.khaledshawki.eoc.platform.integration.connectormanagement.operations;

import io.github.khaledshawki.eoc.connectormanagement.application.model.datasource.SourceIdentity;
import io.github.khaledshawki.eoc.connectormanagement.application.model.datasource.SourcePaymentRecord;
import io.github.khaledshawki.eoc.operations.application.port.in.PaymentImportRecord;
import io.github.khaledshawki.eoc.operations.domain.model.CurrencyCode;
import io.github.khaledshawki.eoc.operations.domain.model.Money;
import io.github.khaledshawki.eoc.operations.domain.model.SourceRecordIdentity;
import io.github.khaledshawki.eoc.operations.domain.model.SourceRecordVersion;
import java.util.Objects;

/** Translates normalized connector Payment facts into the Operations-owned import contract. */
final class OperationsPaymentRecordMapper {

  private OperationsPaymentRecordMapper() {}

  static PaymentImportRecord toOperationsRecord(SourcePaymentRecord record) {
    Objects.requireNonNull(record, "Source payment record cannot be null");
    CurrencyCode currency = CurrencyCode.of(record.currency().getCurrencyCode());
    Money amount = new Money(record.amount(), currency);
    if (!amount.isPositive()) {
      throw new IllegalArgumentException("Payment amount must be positive");
    }

    return new PaymentImportRecord(
        toOperationsIdentity(record.metadata().identity()),
        new SourceRecordVersion(record.metadata().modificationVersion().value()),
        record.metadata().sourceModifiedAt(),
        toOperationsIdentity(record.customerIdentity()),
        amount,
        record.paymentDate(),
        record.reversed());
  }

  private static SourceRecordIdentity toOperationsIdentity(SourceIdentity identity) {
    Objects.requireNonNull(identity, "Source identity cannot be null");
    return switch (identity.kind()) {
      case SOURCE_RECORD_ID -> SourceRecordIdentity.sourceRecordId(identity.value());
      case CANONICAL_RECORD_HASH -> SourceRecordIdentity.canonicalRecordHash(identity.value());
    };
  }
}
