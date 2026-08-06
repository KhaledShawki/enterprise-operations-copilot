package io.github.khaledshawki.eoc.platform.integration.connectormanagement.operations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.khaledshawki.eoc.connectormanagement.application.model.datasource.SourceEntity;
import io.github.khaledshawki.eoc.connectormanagement.application.model.datasource.SourceIdentity;
import io.github.khaledshawki.eoc.connectormanagement.application.model.datasource.SourceInvoiceRecord;
import io.github.khaledshawki.eoc.connectormanagement.application.model.datasource.SourceModificationVersion;
import io.github.khaledshawki.eoc.connectormanagement.application.model.datasource.SourceRecordMetadata;
import io.github.khaledshawki.eoc.operations.application.port.in.InvoiceImportRecord;
import io.github.khaledshawki.eoc.operations.domain.model.SourceRecordIdentity;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Currency;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class OperationsInvoiceRecordMapperTest {

  @Test
  void shouldTranslateInvoiceIdentityCustomerAndPaidAmount() {
    SourceInvoiceRecord source =
        record(
            SourceIdentity.sourceRecordId(SourceEntity.INVOICE, "invoice-1"),
            SourceIdentity.sourceRecordId(SourceEntity.CUSTOMER, "customer-1"),
            "100.00",
            "40.00",
            "partially-paid");

    InvoiceImportRecord translated = OperationsInvoiceRecordMapper.toOperationsRecord(source);

    assertEquals(SourceRecordIdentity.sourceRecordId("invoice-1"), translated.sourceIdentity());
    assertEquals(
        SourceRecordIdentity.sourceRecordId("customer-1"), translated.customerSourceIdentity());
    assertEquals("invoice-1-v2", translated.sourceVersion().value());
    assertEquals(new BigDecimal("100.00"), translated.originalAmount().amount());
    assertEquals(new BigDecimal("60.00"), translated.paidAmount().amount());
    assertEquals("EUR", translated.originalAmount().currency().value());
    assertFalse(translated.cancelled());
  }

  @Test
  void shouldPreserveCanonicalHashesAndRecognizeCancellationAliases() {
    String invoiceHash = "a".repeat(64);
    String customerHash = "b".repeat(64);
    SourceInvoiceRecord source =
        record(
            SourceIdentity.canonicalRecordHash(SourceEntity.INVOICE, invoiceHash),
            SourceIdentity.canonicalRecordHash(SourceEntity.CUSTOMER, customerHash),
            "100.00",
            "25.00",
            " voided ");

    InvoiceImportRecord translated = OperationsInvoiceRecordMapper.toOperationsRecord(source);

    assertEquals(
        SourceRecordIdentity.canonicalRecordHash(invoiceHash), translated.sourceIdentity());
    assertEquals(
        SourceRecordIdentity.canonicalRecordHash(customerHash),
        translated.customerSourceIdentity());
    assertTrue(translated.cancelled());
  }

  @Test
  void shouldRejectInvalidFinancialFacts() {
    assertThrows(
        IllegalArgumentException.class,
        () -> OperationsInvoiceRecordMapper.toOperationsRecord(record("-1.00", "0.00", "OPEN")));
    assertThrows(
        IllegalArgumentException.class,
        () -> OperationsInvoiceRecordMapper.toOperationsRecord(record("100.00", "-1.00", "OPEN")));
    assertThrows(
        IllegalArgumentException.class,
        () -> OperationsInvoiceRecordMapper.toOperationsRecord(record("100.00", "101.00", "OPEN")));
    assertThrows(
        IllegalArgumentException.class,
        () -> OperationsInvoiceRecordMapper.toOperationsRecord(record("100.001", "40.00", "OPEN")));
  }

  @Test
  void shouldRejectUnsupportedSourceStatuses() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            OperationsInvoiceRecordMapper.toOperationsRecord(
                record("100.00", "40.00", "ARCHIVED")));
  }

  private static SourceInvoiceRecord record(
      String totalAmount, String openAmount, String sourceStatus) {
    return record(
        SourceIdentity.sourceRecordId(SourceEntity.INVOICE, "invoice-1"),
        SourceIdentity.sourceRecordId(SourceEntity.CUSTOMER, "customer-1"),
        totalAmount,
        openAmount,
        sourceStatus);
  }

  private static SourceInvoiceRecord record(
      SourceIdentity invoiceIdentity,
      SourceIdentity customerIdentity,
      String totalAmount,
      String openAmount,
      String sourceStatus) {
    return new SourceInvoiceRecord(
        new SourceRecordMetadata(
            invoiceIdentity,
            new SourceModificationVersion("invoice-1-v2"),
            Optional.of(Instant.parse("2026-08-06T08:00:00Z"))),
        customerIdentity,
        "INV-1",
        LocalDate.of(2026, 8, 1),
        LocalDate.of(2026, 8, 31),
        Currency.getInstance("EUR"),
        new BigDecimal(totalAmount),
        new BigDecimal(openAmount),
        sourceStatus);
  }
}
