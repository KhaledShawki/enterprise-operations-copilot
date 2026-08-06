package io.github.khaledshawki.eoc.connectormanagement.application.model.importing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.khaledshawki.eoc.connectormanagement.application.model.datasource.SourceEntity;
import io.github.khaledshawki.eoc.connectormanagement.application.model.datasource.SourceIdentity;
import io.github.khaledshawki.eoc.connectormanagement.application.model.datasource.SourceInvoiceRecord;
import io.github.khaledshawki.eoc.connectormanagement.application.model.datasource.SourceModificationVersion;
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

class InvoiceImportContractTest {

  @Test
  void shouldDefensivelyCopyInvoiceRecords() {
    ArrayList<SourceInvoiceRecord> records = new ArrayList<>(List.of(record("invoice-1")));
    InvoiceImportPage page = page(records);

    records.clear();

    assertEquals(1, page.records().size());
    assertThrows(UnsupportedOperationException.class, () -> page.records().clear());
  }

  @Test
  void shouldRejectNullRecords() {
    ArrayList<SourceInvoiceRecord> records = new ArrayList<>();
    records.add(null);

    assertThrows(NullPointerException.class, () -> page(records));
  }

  @Test
  void shouldAcceptACompleteOutcomeClassification() {
    UUID acceptanceId = UUID.randomUUID();

    InvoiceImportOutcome outcome = new InvoiceImportOutcome(acceptanceId, 5, 2, 1, 2);

    assertEquals(acceptanceId, outcome.pageAcceptanceId());
    assertEquals(5, outcome.fetched());
  }

  @Test
  void shouldRejectInvalidOutcomeCounts() {
    UUID acceptanceId = UUID.randomUUID();

    assertThrows(
        IllegalArgumentException.class, () -> new InvoiceImportOutcome(acceptanceId, 5, 2, 1, 1));
    assertThrows(
        IllegalArgumentException.class, () -> new InvoiceImportOutcome(acceptanceId, -1, 0, 0, 0));
  }

  private static InvoiceImportPage page(List<SourceInvoiceRecord> records) {
    return new InvoiceImportPage(
        UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), records);
  }

  private static SourceInvoiceRecord record(String sourceId) {
    return new SourceInvoiceRecord(
        new SourceRecordMetadata(
            SourceIdentity.sourceRecordId(SourceEntity.INVOICE, sourceId),
            new SourceModificationVersion("v1"),
            Optional.of(Instant.parse("2026-08-06T08:00:00Z"))),
        SourceIdentity.sourceRecordId(SourceEntity.CUSTOMER, "customer-1"),
        "INV-1",
        LocalDate.of(2026, 8, 1),
        LocalDate.of(2026, 8, 31),
        Currency.getInstance("EUR"),
        new BigDecimal("100.00"),
        new BigDecimal("40.00"),
        "PARTIALLY_PAID");
  }
}
