package io.github.khaledshawki.eoc.operations.application.model.importing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import io.github.khaledshawki.eoc.operations.application.port.in.ImportInvoicesCommand;
import io.github.khaledshawki.eoc.operations.application.port.in.InvoiceImportRecord;
import io.github.khaledshawki.eoc.operations.domain.model.CurrencyCode;
import io.github.khaledshawki.eoc.operations.domain.model.InvoiceNumber;
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

class InvoiceImportFingerprintTest {

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
    InvoiceImportRecord record = record("invoice-1", "v1", SOURCE_TIME, "customer-1");

    SourceRecordFingerprint first = InvoiceImportFingerprint.record(record);
    SourceRecordFingerprint second = InvoiceImportFingerprint.record(record);

    assertEquals(first, second);
    assertEquals(SourceRecordFingerprint.SHA_256_HEX_LENGTH, first.value().length());
    assertEquals("46663fa6c71f42a626b206cc3bed41085d72aedc70bdfdb7b31c09b69a1664fd", first.value());
  }

  @Test
  void recordFingerprintShouldCoverBusinessPayloadButExcludeOrderingEvidence() {
    InvoiceImportRecord original = record("invoice-1", "v1", SOURCE_TIME, "customer-1");
    SourceRecordFingerprint fingerprint = InvoiceImportFingerprint.record(original);

    assertEquals(
        fingerprint,
        InvoiceImportFingerprint.record(
            new InvoiceImportRecord(
                SourceRecordIdentity.sourceRecordId("different-invoice-source-id"),
                new SourceRecordVersion("different-version"),
                Optional.of(SOURCE_TIME.plusSeconds(60)),
                original.customerSourceIdentity(),
                original.invoiceNumber(),
                original.originalAmount(),
                original.paidAmount(),
                original.issueDate(),
                original.dueDate(),
                original.cancelled())));

    assertNotEquals(
        fingerprint,
        InvoiceImportFingerprint.record(
            copy(
                original,
                SourceRecordIdentity.sourceRecordId("customer-2"),
                null,
                null,
                null,
                null,
                null,
                null)));
    assertNotEquals(
        fingerprint,
        InvoiceImportFingerprint.record(
            copy(original, null, new InvoiceNumber("INV-CHANGED"), null, null, null, null, null)));
    assertNotEquals(
        fingerprint,
        InvoiceImportFingerprint.record(
            copy(
                original,
                null,
                null,
                Money.of("101.00", CurrencyCode.of("EUR")),
                null,
                null,
                null,
                null)));
    assertNotEquals(
        fingerprint,
        InvoiceImportFingerprint.record(
            copy(
                original,
                null,
                null,
                null,
                Money.of("11.00", CurrencyCode.of("EUR")),
                null,
                null,
                null)));
    assertNotEquals(
        fingerprint,
        InvoiceImportFingerprint.record(
            copy(
                original,
                null,
                null,
                null,
                Money.of("10.00", CurrencyCode.of("USD")),
                null,
                null,
                null)));
    assertNotEquals(
        fingerprint,
        InvoiceImportFingerprint.record(
            copy(original, null, null, null, null, LocalDate.parse("2026-08-02"), null, null)));
    assertNotEquals(
        fingerprint,
        InvoiceImportFingerprint.record(
            copy(original, null, null, null, null, null, LocalDate.parse("2026-09-02"), null)));
    assertNotEquals(
        fingerprint,
        InvoiceImportFingerprint.record(copy(original, null, null, null, null, null, null, true)));
  }

  @Test
  void pageFingerprintShouldCoverIdentityOrderingEvidencePayloadAndRecordOrder() {
    InvoiceImportRecord first = record("invoice-1", "v1", SOURCE_TIME, "customer-1");
    InvoiceImportRecord second = record("invoice-2", "v1", SOURCE_TIME, "customer-1");
    String fingerprint = InvoiceImportFingerprint.page(command(List.of(first, second)));

    assertEquals(fingerprint, InvoiceImportFingerprint.page(command(List.of(first, second))));
    assertNotEquals(fingerprint, InvoiceImportFingerprint.page(command(List.of(second, first))));
    assertNotEquals(
        fingerprint,
        InvoiceImportFingerprint.page(
            command(
                List.of(
                    new InvoiceImportRecord(
                        SourceRecordIdentity.sourceRecordId("different"),
                        first.sourceVersion(),
                        first.sourceModifiedAt(),
                        first.customerSourceIdentity(),
                        first.invoiceNumber(),
                        first.originalAmount(),
                        first.paidAmount(),
                        first.issueDate(),
                        first.dueDate(),
                        first.cancelled()),
                    second))));
    assertNotEquals(
        fingerprint,
        InvoiceImportFingerprint.page(
            command(
                List.of(
                    new InvoiceImportRecord(
                        first.sourceIdentity(),
                        new SourceRecordVersion("v2"),
                        first.sourceModifiedAt(),
                        first.customerSourceIdentity(),
                        first.invoiceNumber(),
                        first.originalAmount(),
                        first.paidAmount(),
                        first.issueDate(),
                        first.dueDate(),
                        first.cancelled()),
                    second))));
    assertNotEquals(
        fingerprint,
        InvoiceImportFingerprint.page(
            command(
                List.of(
                    new InvoiceImportRecord(
                        first.sourceIdentity(),
                        first.sourceVersion(),
                        Optional.empty(),
                        first.customerSourceIdentity(),
                        first.invoiceNumber(),
                        first.originalAmount(),
                        first.paidAmount(),
                        first.issueDate(),
                        first.dueDate(),
                        first.cancelled()),
                    second))));
    assertNotEquals(
        fingerprint,
        InvoiceImportFingerprint.page(
            command(
                List.of(
                    copy(
                        first,
                        null,
                        null,
                        Money.of("102.00", CurrencyCode.of("EUR")),
                        null,
                        null,
                        null,
                        null),
                    second))));
  }

  private static ImportInvoicesCommand command(List<InvoiceImportRecord> records) {
    return new ImportInvoicesCommand(
        TENANT_ID, SOURCE_SYSTEM_ID, IMPORT_BATCH_ID, PAGE_ACCEPTANCE_ID, records);
  }

  private static InvoiceImportRecord record(
      String sourceIdentity, String version, Instant modifiedAt, String customerIdentity) {
    return new InvoiceImportRecord(
        SourceRecordIdentity.sourceRecordId(sourceIdentity),
        new SourceRecordVersion(version),
        Optional.ofNullable(modifiedAt),
        SourceRecordIdentity.sourceRecordId(customerIdentity),
        new InvoiceNumber("INV-1"),
        Money.of("100.00", CurrencyCode.of("EUR")),
        Money.of("10.00", CurrencyCode.of("EUR")),
        LocalDate.parse("2026-08-01"),
        LocalDate.parse("2026-09-01"),
        false);
  }

  private static InvoiceImportRecord copy(
      InvoiceImportRecord original,
      SourceRecordIdentity customerSourceIdentity,
      InvoiceNumber invoiceNumber,
      Money originalAmount,
      Money paidAmount,
      LocalDate issueDate,
      LocalDate dueDate,
      Boolean cancelled) {
    return new InvoiceImportRecord(
        original.sourceIdentity(),
        original.sourceVersion(),
        original.sourceModifiedAt(),
        customerSourceIdentity == null ? original.customerSourceIdentity() : customerSourceIdentity,
        invoiceNumber == null ? original.invoiceNumber() : invoiceNumber,
        originalAmount == null ? original.originalAmount() : originalAmount,
        paidAmount == null ? original.paidAmount() : paidAmount,
        issueDate == null ? original.issueDate() : issueDate,
        dueDate == null ? original.dueDate() : dueDate,
        cancelled == null ? original.cancelled() : cancelled);
  }
}
