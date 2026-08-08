package io.github.khaledshawki.eoc.operations.application.port.in;

import io.github.khaledshawki.eoc.operations.domain.model.Money;
import io.github.khaledshawki.eoc.operations.domain.model.SourceRecordIdentity;
import io.github.khaledshawki.eoc.operations.domain.model.SourceRecordVersion;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

public record PaymentImportRecord(
    SourceRecordIdentity sourceIdentity,
    SourceRecordVersion sourceVersion,
    Optional<Instant> sourceModifiedAt,
    SourceRecordIdentity customerSourceIdentity,
    Money amount,
    LocalDate paymentDate,
    boolean reversed) {

  public PaymentImportRecord {
    Objects.requireNonNull(sourceIdentity, "Payment source identity cannot be null");
    Objects.requireNonNull(sourceVersion, "Payment source version cannot be null");
    Objects.requireNonNull(
        sourceModifiedAt, "Payment source modification timestamp cannot be null");
    Objects.requireNonNull(
        customerSourceIdentity, "Payment customer source identity cannot be null");
    Objects.requireNonNull(amount, "Payment amount cannot be null");
    Objects.requireNonNull(paymentDate, "Payment date cannot be null");
  }
}
