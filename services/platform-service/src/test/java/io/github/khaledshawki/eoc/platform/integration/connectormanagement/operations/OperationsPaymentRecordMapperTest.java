package io.github.khaledshawki.eoc.platform.integration.connectormanagement.operations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.khaledshawki.eoc.connectormanagement.application.model.datasource.SourceEntity;
import io.github.khaledshawki.eoc.connectormanagement.application.model.datasource.SourceIdentity;
import io.github.khaledshawki.eoc.connectormanagement.application.model.datasource.SourceModificationVersion;
import io.github.khaledshawki.eoc.connectormanagement.application.model.datasource.SourcePaymentRecord;
import io.github.khaledshawki.eoc.connectormanagement.application.model.datasource.SourceRecordMetadata;
import io.github.khaledshawki.eoc.operations.application.port.in.PaymentImportRecord;
import io.github.khaledshawki.eoc.operations.domain.model.SourceRecordIdentity;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Currency;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class OperationsPaymentRecordMapperTest {

  @Test
  void shouldTranslatePaymentIdentityCustomerMoneyAndEvidence() {
    SourcePaymentRecord source =
        record(
            SourceIdentity.sourceRecordId(SourceEntity.PAYMENT, "payment-1"),
            SourceIdentity.sourceRecordId(SourceEntity.CUSTOMER, "customer-1"),
            "100.00",
            false);

    PaymentImportRecord translated = OperationsPaymentRecordMapper.toOperationsRecord(source);

    assertEquals(SourceRecordIdentity.sourceRecordId("payment-1"), translated.sourceIdentity());
    assertEquals(
        SourceRecordIdentity.sourceRecordId("customer-1"), translated.customerSourceIdentity());
    assertEquals("payment-1-v2", translated.sourceVersion().value());
    assertEquals(Optional.of(Instant.parse("2026-08-06T08:00:00Z")), translated.sourceModifiedAt());
    assertEquals(new BigDecimal("100.00"), translated.amount().amount());
    assertEquals("EUR", translated.amount().currency().value());
    assertEquals(LocalDate.of(2026, 8, 1), translated.paymentDate());
    assertFalse(translated.reversed());
  }

  @Test
  void shouldPreserveCanonicalHashesAndReversalState() {
    String paymentHash = "a".repeat(64);
    String customerHash = "b".repeat(64);
    SourcePaymentRecord source =
        record(
            SourceIdentity.canonicalRecordHash(SourceEntity.PAYMENT, paymentHash),
            SourceIdentity.canonicalRecordHash(SourceEntity.CUSTOMER, customerHash),
            "25.00",
            true);

    PaymentImportRecord translated = OperationsPaymentRecordMapper.toOperationsRecord(source);

    assertEquals(
        SourceRecordIdentity.canonicalRecordHash(paymentHash), translated.sourceIdentity());
    assertEquals(
        SourceRecordIdentity.canonicalRecordHash(customerHash),
        translated.customerSourceIdentity());
    assertTrue(translated.reversed());
  }

  @Test
  void shouldRejectNonPositiveAndUnsupportedPrecisionAmounts() {
    assertThrows(
        IllegalArgumentException.class,
        () -> OperationsPaymentRecordMapper.toOperationsRecord(record("0.00", false)));
    assertThrows(
        IllegalArgumentException.class,
        () -> OperationsPaymentRecordMapper.toOperationsRecord(record("-1.00", false)));
    assertThrows(
        IllegalArgumentException.class,
        () -> OperationsPaymentRecordMapper.toOperationsRecord(record("1.001", false)));
  }

  @Test
  void shouldRejectNullRecord() {
    assertThrows(
        NullPointerException.class, () -> OperationsPaymentRecordMapper.toOperationsRecord(null));
  }

  private static SourcePaymentRecord record(String amount, boolean reversed) {
    return record(
        SourceIdentity.sourceRecordId(SourceEntity.PAYMENT, "payment-1"),
        SourceIdentity.sourceRecordId(SourceEntity.CUSTOMER, "customer-1"),
        amount,
        reversed);
  }

  private static SourcePaymentRecord record(
      SourceIdentity paymentIdentity,
      SourceIdentity customerIdentity,
      String amount,
      boolean reversed) {
    return new SourcePaymentRecord(
        new SourceRecordMetadata(
            paymentIdentity,
            new SourceModificationVersion("payment-1-v2"),
            Optional.of(Instant.parse("2026-08-06T08:00:00Z"))),
        customerIdentity,
        LocalDate.of(2026, 8, 1),
        Currency.getInstance("EUR"),
        new BigDecimal(amount),
        reversed);
  }
}
