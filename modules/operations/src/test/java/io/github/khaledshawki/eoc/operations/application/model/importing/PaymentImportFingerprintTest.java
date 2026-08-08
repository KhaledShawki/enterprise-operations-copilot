package io.github.khaledshawki.eoc.operations.application.model.importing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import io.github.khaledshawki.eoc.operations.application.port.in.ImportPaymentsCommand;
import io.github.khaledshawki.eoc.operations.application.port.in.PaymentImportRecord;
import io.github.khaledshawki.eoc.operations.domain.model.CurrencyCode;
import io.github.khaledshawki.eoc.operations.domain.model.Money;
import io.github.khaledshawki.eoc.operations.domain.model.SourceRecordFingerprint;
import io.github.khaledshawki.eoc.operations.domain.model.SourceRecordIdentity;
import io.github.khaledshawki.eoc.operations.domain.model.SourceRecordVersion;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PaymentImportFingerprintTest {

  private static final UUID TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
  private static final UUID SOURCE_SYSTEM_ID =
      UUID.fromString("00000000-0000-0000-0000-000000000010");
  private static final UUID IMPORT_BATCH_ID =
      UUID.fromString("00000000-0000-0000-0000-000000000020");
  private static final UUID PAGE_ACCEPTANCE_ID =
      UUID.fromString("00000000-0000-0000-0000-000000000030");
  private static final Instant SOURCE_TIME = Instant.parse("2026-08-01T08:00:00Z");

  @Test
  void shouldProduceDeterministicCanonicalRecordFingerprints() {
    PaymentImportRecord record =
        record("payment-1", "v1", SOURCE_TIME, "customer-1", "100.00", false);

    SourceRecordFingerprint first = PaymentImportFingerprint.record(record);
    SourceRecordFingerprint second = PaymentImportFingerprint.record(record);

    assertEquals(first, second);
    assertEquals(SourceRecordFingerprint.SHA_256_HEX_LENGTH, first.value().length());
    assertEquals("dcb3963b4414bbc7ab19e6a8c92ea241e11926a465817896c54112c57dd3f073", first.value());
  }

  @Test
  void recordFingerprintShouldCoverBusinessPayloadButExcludeOrderingEvidence() {
    PaymentImportRecord original =
        record("payment-1", "v1", SOURCE_TIME, "customer-1", "100.00", false);
    SourceRecordFingerprint fingerprint = PaymentImportFingerprint.record(original);

    assertEquals(
        fingerprint,
        PaymentImportFingerprint.record(
            new PaymentImportRecord(
                SourceRecordIdentity.sourceRecordId("different-payment-source-id"),
                new SourceRecordVersion("different-version"),
                Optional.of(SOURCE_TIME.plusSeconds(60)),
                original.customerSourceIdentity(),
                original.amount(),
                original.paymentDate(),
                original.reversed())));

    assertNotEquals(
        fingerprint,
        PaymentImportFingerprint.record(
            copy(original, SourceRecordIdentity.sourceRecordId("customer-2"), null, null, null)));
    assertNotEquals(
        fingerprint,
        PaymentImportFingerprint.record(
            copy(original, null, Money.of("101.00", CurrencyCode.of("EUR")), null, null)));
    assertNotEquals(
        fingerprint,
        PaymentImportFingerprint.record(
            copy(original, null, Money.of("100.00", CurrencyCode.of("USD")), null, null)));
    assertNotEquals(
        fingerprint,
        PaymentImportFingerprint.record(
            copy(original, null, null, LocalDate.parse("2026-08-02"), null)));
    assertNotEquals(
        fingerprint, PaymentImportFingerprint.record(copy(original, null, null, null, true)));
  }

  @Test
  void pageFingerprintShouldCoverIdentityOrderingEvidencePayloadAndRecordOrder() {
    PaymentImportRecord first =
        record("payment-1", "v1", SOURCE_TIME, "customer-1", "100.00", false);
    PaymentImportRecord second =
        record("payment-2", "v1", SOURCE_TIME, "customer-1", "50.00", false);
    String fingerprint = PaymentImportFingerprint.page(command(List.of(first, second)));

    assertEquals(fingerprint, PaymentImportFingerprint.page(command(List.of(first, second))));
    assertNotEquals(fingerprint, PaymentImportFingerprint.page(command(List.of(second, first))));
    assertNotEquals(
        fingerprint,
        PaymentImportFingerprint.page(
            command(
                List.of(
                    new PaymentImportRecord(
                        SourceRecordIdentity.sourceRecordId("different"),
                        first.sourceVersion(),
                        first.sourceModifiedAt(),
                        first.customerSourceIdentity(),
                        first.amount(),
                        first.paymentDate(),
                        first.reversed()),
                    second))));
    assertNotEquals(
        fingerprint,
        PaymentImportFingerprint.page(
            command(
                List.of(
                    new PaymentImportRecord(
                        first.sourceIdentity(),
                        new SourceRecordVersion("v2"),
                        first.sourceModifiedAt(),
                        first.customerSourceIdentity(),
                        first.amount(),
                        first.paymentDate(),
                        first.reversed()),
                    second))));
    assertNotEquals(
        fingerprint,
        PaymentImportFingerprint.page(
            command(
                List.of(
                    new PaymentImportRecord(
                        first.sourceIdentity(),
                        first.sourceVersion(),
                        Optional.empty(),
                        first.customerSourceIdentity(),
                        first.amount(),
                        first.paymentDate(),
                        first.reversed()),
                    second))));
    assertNotEquals(
        fingerprint,
        PaymentImportFingerprint.page(
            command(
                List.of(
                    copy(first, null, Money.of("102.00", CurrencyCode.of("EUR")), null, null),
                    second))));
  }

  private static ImportPaymentsCommand command(List<PaymentImportRecord> records) {
    return new ImportPaymentsCommand(
        TENANT_ID, SOURCE_SYSTEM_ID, IMPORT_BATCH_ID, PAGE_ACCEPTANCE_ID, records);
  }

  private static PaymentImportRecord record(
      String sourceIdentity,
      String version,
      Instant modifiedAt,
      String customerIdentity,
      String amount,
      boolean reversed) {
    return new PaymentImportRecord(
        SourceRecordIdentity.sourceRecordId(sourceIdentity),
        new SourceRecordVersion(version),
        Optional.ofNullable(modifiedAt),
        SourceRecordIdentity.sourceRecordId(customerIdentity),
        Money.of(amount, CurrencyCode.of("EUR")),
        LocalDate.parse("2026-08-01"),
        reversed);
  }

  private static PaymentImportRecord copy(
      PaymentImportRecord original,
      SourceRecordIdentity customerSourceIdentity,
      Money amount,
      LocalDate paymentDate,
      Boolean reversed) {
    return new PaymentImportRecord(
        original.sourceIdentity(),
        original.sourceVersion(),
        original.sourceModifiedAt(),
        customerSourceIdentity == null ? original.customerSourceIdentity() : customerSourceIdentity,
        amount == null ? original.amount() : amount,
        paymentDate == null ? original.paymentDate() : paymentDate,
        reversed == null ? original.reversed() : reversed);
  }
}
