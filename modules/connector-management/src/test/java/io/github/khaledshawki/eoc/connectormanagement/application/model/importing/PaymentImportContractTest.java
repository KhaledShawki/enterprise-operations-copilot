package io.github.khaledshawki.eoc.connectormanagement.application.model.importing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.khaledshawki.eoc.connectormanagement.application.model.datasource.SourceEntity;
import io.github.khaledshawki.eoc.connectormanagement.application.model.datasource.SourceIdentity;
import io.github.khaledshawki.eoc.connectormanagement.application.model.datasource.SourceModificationVersion;
import io.github.khaledshawki.eoc.connectormanagement.application.model.datasource.SourcePaymentRecord;
import io.github.khaledshawki.eoc.connectormanagement.application.model.datasource.SourceRecordMetadata;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PaymentImportContractTest {

  @Test
  void shouldDefensivelyCopyPaymentRecords() {
    ArrayList<SourcePaymentRecord> records = new ArrayList<>(List.of(record("payment-1", false)));
    PaymentImportPage page = page(records);

    records.clear();

    assertEquals(1, page.records().size());
    assertThrows(UnsupportedOperationException.class, () -> page.records().clear());
  }

  @Test
  void shouldRejectNullRecordsAndMismatchedSourceEntities() {
    ArrayList<SourcePaymentRecord> records = new ArrayList<>();
    records.add(null);
    assertThrows(NullPointerException.class, () -> page(records));

    SourceModificationVersion version = new SourceModificationVersion("v1");
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new SourcePaymentRecord(
                SourceRecordMetadata.withoutModificationTimestamp(
                    SourceIdentity.sourceRecordId(SourceEntity.INVOICE, "invoice-1"), version),
                SourceIdentity.sourceRecordId(SourceEntity.CUSTOMER, "customer-1"),
                LocalDate.of(2026, 8, 1),
                Currency.getInstance("EUR"),
                BigDecimal.ONE,
                false));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new SourcePaymentRecord(
                SourceRecordMetadata.withoutModificationTimestamp(
                    SourceIdentity.sourceRecordId(SourceEntity.PAYMENT, "payment-1"), version),
                SourceIdentity.sourceRecordId(SourceEntity.PAYMENT, "not-a-customer"),
                LocalDate.of(2026, 8, 1),
                Currency.getInstance("EUR"),
                BigDecimal.ONE,
                false));
  }

  @Test
  void shouldPreserveNormalizedPaymentFactsAndSourceEvidence() {
    SourcePaymentRecord payment = record("payment-1", true);

    assertEquals(SourceEntity.PAYMENT, payment.metadata().identity().entity());
    assertEquals("payment-1-v1", payment.metadata().modificationVersion().value());
    assertEquals(
        Optional.of(Instant.parse("2026-08-06T08:00:00Z")), payment.metadata().sourceModifiedAt());
    assertEquals(SourceEntity.CUSTOMER, payment.customerIdentity().entity());
    assertEquals(new BigDecimal("25.00"), payment.amount());
    assertEquals(LocalDate.of(2026, 8, 1), payment.paymentDate());
    assertTrue(payment.reversed());
  }

  @Test
  void shouldAcceptCompleteOutcomeClassification() {
    UUID acceptanceId = UUID.randomUUID();

    PaymentImportOutcome outcome = new PaymentImportOutcome(acceptanceId, 5, 2, 1, 2);

    assertEquals(acceptanceId, outcome.pageAcceptanceId());
    assertEquals(5, outcome.fetched());
    assertEquals(2, outcome.accepted());
    assertEquals(1, outcome.rejected());
    assertEquals(2, outcome.duplicates());
  }

  @Test
  void shouldRejectInvalidOutcomeCounts() {
    UUID acceptanceId = UUID.randomUUID();

    assertThrows(
        IllegalArgumentException.class, () -> new PaymentImportOutcome(acceptanceId, 5, 2, 1, 1));
    assertThrows(
        IllegalArgumentException.class, () -> new PaymentImportOutcome(acceptanceId, -1, 0, 0, 0));
    assertThrows(
        ArithmeticException.class,
        () -> new PaymentImportOutcome(acceptanceId, Long.MAX_VALUE, Long.MAX_VALUE, 1, 0));
  }

  private static PaymentImportPage page(List<SourcePaymentRecord> records) {
    return new PaymentImportPage(
        UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), records);
  }

  private static SourcePaymentRecord record(String sourceId, boolean reversed) {
    return new SourcePaymentRecord(
        new SourceRecordMetadata(
            SourceIdentity.sourceRecordId(SourceEntity.PAYMENT, sourceId),
            new SourceModificationVersion(sourceId + "-v1"),
            Optional.of(Instant.parse("2026-08-06T08:00:00Z"))),
        SourceIdentity.sourceRecordId(SourceEntity.CUSTOMER, "customer-1"),
        LocalDate.of(2026, 8, 1),
        Currency.getInstance("EUR"),
        new BigDecimal("25.00"),
        reversed);
  }
}
