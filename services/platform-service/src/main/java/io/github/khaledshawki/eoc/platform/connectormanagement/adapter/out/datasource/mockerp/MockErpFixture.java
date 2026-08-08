package io.github.khaledshawki.eoc.platform.connectormanagement.adapter.out.datasource.mockerp;

import static io.github.khaledshawki.eoc.connectormanagement.application.model.datasource.SourceEntity.CUSTOMER;
import static io.github.khaledshawki.eoc.connectormanagement.application.model.datasource.SourceEntity.INVOICE;
import static io.github.khaledshawki.eoc.connectormanagement.application.model.datasource.SourceEntity.PAYMENT;

import io.github.khaledshawki.eoc.connectormanagement.application.model.datasource.SourceCustomerRecord;
import io.github.khaledshawki.eoc.connectormanagement.application.model.datasource.SourceEntity;
import io.github.khaledshawki.eoc.connectormanagement.application.model.datasource.SourceIdentity;
import io.github.khaledshawki.eoc.connectormanagement.application.model.datasource.SourceInvoiceRecord;
import io.github.khaledshawki.eoc.connectormanagement.application.model.datasource.SourceModificationVersion;
import io.github.khaledshawki.eoc.connectormanagement.application.model.datasource.SourcePaymentRecord;
import io.github.khaledshawki.eoc.connectormanagement.application.model.datasource.SourceRecordMetadata;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Currency;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

final class MockErpFixture {

  private final List<FixtureRecord<SourceCustomerRecord>> customers;
  private final List<FixtureRecord<SourceInvoiceRecord>> invoices;
  private final List<FixtureRecord<SourcePaymentRecord>> payments;

  private MockErpFixture(
      List<FixtureRecord<SourceCustomerRecord>> customers,
      List<FixtureRecord<SourceInvoiceRecord>> invoices,
      List<FixtureRecord<SourcePaymentRecord>> payments) {
    this.customers = List.copyOf(customers);
    this.invoices = List.copyOf(invoices);
    this.payments = List.copyOf(payments);
  }

  static MockErpFixture standard() {
    return new MockErpFixture(
        List.of(
            customer(
                1,
                "customer-1000",
                "customer-1000-v1",
                "2026-01-05T09:00:00Z",
                "C1000",
                "Acme Manufacturing",
                Optional.of("accounts@acme.example")),
            customer(
                2,
                "customer-2000",
                "customer-2000-v3",
                "2026-01-12T10:15:00Z",
                "C2000",
                "Northwind Traders",
                Optional.of("finance@northwind.example")),
            customer(
                3,
                "customer-3000",
                "customer-3000-v2",
                "2026-02-02T14:30:00Z",
                "C3000",
                "Globex Corporation",
                Optional.empty())),
        List.of(
            invoice(
                1,
                "invoice-1000",
                "invoice-1000-v1",
                "2026-01-05T11:00:00Z",
                "customer-1000",
                "INV-1000",
                LocalDate.of(2026, 1, 5),
                LocalDate.of(2026, 2, 4),
                "1250.00",
                "1250.00",
                "OPEN"),
            invoice(
                2,
                "invoice-1001",
                "invoice-1001-v2",
                "2026-01-18T08:45:00Z",
                "customer-1000",
                "INV-1001",
                LocalDate.of(2026, 1, 12),
                LocalDate.of(2026, 2, 11),
                "400.00",
                "0.00",
                "PAID"),
            invoice(
                3,
                "invoice-2000",
                "invoice-2000-v4",
                "2026-02-10T16:20:00Z",
                "customer-2000",
                "INV-2000",
                LocalDate.of(2026, 2, 2),
                LocalDate.of(2026, 3, 4),
                "875.50",
                "275.50",
                "PARTIALLY_PAID")),
        List.of(
            payment(
                1,
                "payment-1000",
                "payment-1000-v1",
                "2026-01-18T09:00:00Z",
                "customer-1000",
                LocalDate.of(2026, 1, 18),
                "400.00",
                false),
            payment(
                2,
                "payment-2000",
                "payment-2000-v1",
                "2026-02-10T17:00:00Z",
                "customer-2000",
                LocalDate.of(2026, 2, 10),
                "600.00",
                false),
            payment(
                3,
                "payment-2001",
                "payment-2001-v2",
                "2026-02-12T10:00:00Z",
                "customer-2000",
                LocalDate.of(2026, 2, 11),
                "125.00",
                true)));
  }

  List<FixtureRecord<SourceCustomerRecord>> customers() {
    return customers;
  }

  List<FixtureRecord<SourceInvoiceRecord>> invoices() {
    return invoices;
  }

  List<FixtureRecord<SourcePaymentRecord>> payments() {
    return payments;
  }

  private static FixtureRecord<SourceCustomerRecord> customer(
      long sequence,
      String sourceId,
      String sourceVersion,
      String sourceModifiedAt,
      String customerNumber,
      String displayName,
      Optional<String> emailAddress) {
    return new FixtureRecord<>(
        sequence,
        new SourceCustomerRecord(
            metadata(CUSTOMER, sourceId, sourceVersion, sourceModifiedAt),
            customerNumber,
            displayName,
            emailAddress));
  }

  private static FixtureRecord<SourceInvoiceRecord> invoice(
      long sequence,
      String sourceId,
      String sourceVersion,
      String sourceModifiedAt,
      String customerSourceId,
      String invoiceNumber,
      LocalDate issueDate,
      LocalDate dueDate,
      String totalAmount,
      String openAmount,
      String sourceStatus) {
    return new FixtureRecord<>(
        sequence,
        new SourceInvoiceRecord(
            metadata(INVOICE, sourceId, sourceVersion, sourceModifiedAt),
            SourceIdentity.sourceRecordId(CUSTOMER, customerSourceId),
            invoiceNumber,
            issueDate,
            dueDate,
            Currency.getInstance("USD"),
            new BigDecimal(totalAmount),
            new BigDecimal(openAmount),
            sourceStatus));
  }

  private static FixtureRecord<SourcePaymentRecord> payment(
      long sequence,
      String sourceId,
      String sourceVersion,
      String sourceModifiedAt,
      String customerSourceId,
      LocalDate paymentDate,
      String amount,
      boolean reversed) {
    return new FixtureRecord<>(
        sequence,
        new SourcePaymentRecord(
            metadata(PAYMENT, sourceId, sourceVersion, sourceModifiedAt),
            SourceIdentity.sourceRecordId(CUSTOMER, customerSourceId),
            paymentDate,
            Currency.getInstance("USD"),
            new BigDecimal(amount),
            reversed));
  }

  private static SourceRecordMetadata metadata(
      SourceEntity entity, String sourceId, String sourceVersion, String sourceModifiedAt) {
    return new SourceRecordMetadata(
        SourceIdentity.sourceRecordId(entity, sourceId),
        new SourceModificationVersion(sourceVersion),
        Optional.of(Instant.parse(sourceModifiedAt)));
  }

  record FixtureRecord<T>(long sequence, T record) {

    FixtureRecord {
      if (sequence < 1) {
        throw new IllegalArgumentException("Mock ERP fixture sequence must be positive");
      }
      Objects.requireNonNull(record, "Mock ERP fixture record cannot be null");
    }
  }
}
