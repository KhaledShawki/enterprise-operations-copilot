package io.github.khaledshawki.eoc.platform.operations.adapter.out.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.khaledshawki.eoc.operations.application.model.reconciliation.ReceivableReconciliationEvidence;
import io.github.khaledshawki.eoc.operations.application.model.reconciliation.ReceivableReconciliationIssue;
import io.github.khaledshawki.eoc.operations.application.port.out.ReceivableReconciliationEvidenceRepository;
import io.github.khaledshawki.eoc.operations.domain.model.BusinessPartnerId;
import io.github.khaledshawki.eoc.operations.domain.model.CurrencyCode;
import io.github.khaledshawki.eoc.operations.domain.model.InvoiceId;
import io.github.khaledshawki.eoc.operations.domain.model.Money;
import io.github.khaledshawki.eoc.operations.domain.model.OperationsTenantId;
import io.github.khaledshawki.eoc.platform.TestcontainersConfiguration;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class ReceivableReconciliationEvidencePersistenceIT {

  private static final CurrencyCode EUR = CurrencyCode.of("EUR");
  private static final UUID TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000101");
  private static final UUID CUSTOMER_ID = UUID.fromString("00000000-0000-0000-0000-000000000201");
  private static final UUID OTHER_CUSTOMER_ID =
      UUID.fromString("00000000-0000-0000-0000-000000000202");
  private static final UUID INVOICE_ID = UUID.fromString("00000000-0000-0000-0000-000000000301");
  private static final UUID OTHER_INVOICE_ID =
      UUID.fromString("00000000-0000-0000-0000-000000000302");
  private static final UUID PAYMENT_ID = UUID.fromString("00000000-0000-0000-0000-000000000401");
  private static final UUID OTHER_PAYMENT_ID =
      UUID.fromString("00000000-0000-0000-0000-000000000402");
  private static final UUID SETTLEMENT_ID = UUID.fromString("00000000-0000-0000-0000-000000000501");
  private static final UUID OTHER_SETTLEMENT_ID =
      UUID.fromString("00000000-0000-0000-0000-000000000502");

  @Autowired private ReceivableReconciliationEvidenceRepository evidenceRepository;
  @Autowired private JdbcTemplate jdbcTemplate;

  @BeforeEach
  void setUp() {
    jdbcTemplate.update("DELETE FROM operations_receivable_allocations");
    jdbcTemplate.update("DELETE FROM operations_receivable_settlements");
    jdbcTemplate.update("DELETE FROM operations_receivable_settlement_locks");
    jdbcTemplate.update("DELETE FROM operations_payment_source_mappings");
    jdbcTemplate.update("DELETE FROM operations_payments");
    jdbcTemplate.update("DELETE FROM operations_invoice_source_mappings");
    jdbcTemplate.update("DELETE FROM operations_invoices");
    jdbcTemplate.update("DELETE FROM operations_business_partner_source_mappings");
    jdbcTemplate.update("DELETE FROM operations_business_partner_roles");
    jdbcTemplate.update("DELETE FROM operations_business_partners");
  }

  @Test
  void shouldReturnCanonicalZeroWhenInvoiceHasNoActiveAllocations() {
    seedCustomer(CUSTOMER_ID, "C-1");
    seedInvoice(INVOICE_ID, CUSTOMER_ID, "EUR", "100.00", "0.00");

    ReceivableReconciliationEvidence evidence = load();

    assertEquals(0, evidence.activeAllocationCount());
    assertEquals(Money.zero(EUR), evidence.localAllocatedAmount().orElseThrow());
    assertTrue(evidence.issues().isEmpty());
  }

  @Test
  void shouldSumActiveAllocationsAcrossPaymentsAndIgnoreReversedHistory() {
    seedCustomer(CUSTOMER_ID, "C-1");
    seedInvoice(INVOICE_ID, CUSTOMER_ID, "EUR", "100.00", "0.00");
    seedPayment(PAYMENT_ID, CUSTOMER_ID, "EUR", "100.00", false);
    seedPayment(OTHER_PAYMENT_ID, CUSTOMER_ID, "EUR", "100.00", false);
    insertSettlement(SETTLEMENT_ID, CUSTOMER_ID, PAYMENT_ID, "EUR");
    insertSettlement(OTHER_SETTLEMENT_ID, CUSTOMER_ID, OTHER_PAYMENT_ID, "EUR");
    insertAllocation(UUID.randomUUID(), SETTLEMENT_ID, INVOICE_ID, "EUR", "20.00", "ACTIVE", 0);
    insertAllocation(
        UUID.randomUUID(), OTHER_SETTLEMENT_ID, INVOICE_ID, "EUR", "30.00", "ACTIVE", 0);
    insertAllocation(
        UUID.randomUUID(), OTHER_SETTLEMENT_ID, INVOICE_ID, "EUR", "99.00", "REVERSED", 1);

    ReceivableReconciliationEvidence evidence = load();

    assertEquals(2, evidence.activeAllocationCount());
    assertEquals(Money.of("50.00", EUR), evidence.localAllocatedAmount().orElseThrow());
    assertTrue(evidence.issues().isEmpty());
  }

  @Test
  void shouldRefuseToInventLocalMoneyAcrossAllocationCurrencies() {
    seedCustomer(CUSTOMER_ID, "C-1");
    seedInvoice(INVOICE_ID, CUSTOMER_ID, "EUR", "100.00", "0.00");
    seedPayment(PAYMENT_ID, CUSTOMER_ID, "EUR", "100.00", false);
    insertSettlement(SETTLEMENT_ID, CUSTOMER_ID, PAYMENT_ID, "EUR");
    insertAllocation(UUID.randomUUID(), SETTLEMENT_ID, INVOICE_ID, "USD", "20.00", "ACTIVE", 0);

    ReceivableReconciliationEvidence evidence = load();

    assertTrue(evidence.localAllocatedAmount().isEmpty());
    assertTrue(
        evidence.issues().contains(ReceivableReconciliationIssue.ALLOCATION_CURRENCY_MISMATCH));
    assertTrue(
        evidence
            .issues()
            .contains(ReceivableReconciliationIssue.PAYMENT_ALLOCATION_CURRENCY_CONFLICT));
  }

  @Test
  void shouldDetectSettlementAndPaymentCustomerCorrections() {
    seedCustomer(CUSTOMER_ID, "C-1");
    seedCustomer(OTHER_CUSTOMER_ID, "C-2");
    seedInvoice(INVOICE_ID, CUSTOMER_ID, "EUR", "100.00", "0.00");
    seedPayment(PAYMENT_ID, OTHER_CUSTOMER_ID, "EUR", "100.00", false);
    insertSettlement(SETTLEMENT_ID, OTHER_CUSTOMER_ID, PAYMENT_ID, "EUR");
    insertAllocation(UUID.randomUUID(), SETTLEMENT_ID, INVOICE_ID, "EUR", "20.00", "ACTIVE", 0);

    ReceivableReconciliationEvidence evidence = load();

    assertEquals(Money.of("20.00", EUR), evidence.localAllocatedAmount().orElseThrow());
    assertTrue(
        evidence.issues().contains(ReceivableReconciliationIssue.SETTLEMENT_CUSTOMER_MISMATCH));
    assertTrue(evidence.issues().contains(ReceivableReconciliationIssue.PAYMENT_CUSTOMER_MISMATCH));
  }

  @Test
  void shouldDetectSettlementAndPaymentCurrencyCorrections() {
    seedCustomer(CUSTOMER_ID, "C-1");
    seedInvoice(INVOICE_ID, CUSTOMER_ID, "EUR", "100.00", "0.00");
    seedPayment(PAYMENT_ID, CUSTOMER_ID, "USD", "100.00", false);
    insertSettlement(SETTLEMENT_ID, CUSTOMER_ID, PAYMENT_ID, "USD");
    insertAllocation(UUID.randomUUID(), SETTLEMENT_ID, INVOICE_ID, "EUR", "20.00", "ACTIVE", 0);

    ReceivableReconciliationEvidence evidence = load();

    assertEquals(Money.of("20.00", EUR), evidence.localAllocatedAmount().orElseThrow());
    assertTrue(
        evidence.issues().contains(ReceivableReconciliationIssue.SETTLEMENT_CURRENCY_MISMATCH));
    assertTrue(evidence.issues().contains(ReceivableReconciliationIssue.PAYMENT_CURRENCY_MISMATCH));
    assertTrue(
        evidence
            .issues()
            .contains(ReceivableReconciliationIssue.PAYMENT_ALLOCATION_CURRENCY_CONFLICT));
  }

  @Test
  void shouldDetectReversedPaymentThatStillBacksActiveAllocation() {
    seedCustomer(CUSTOMER_ID, "C-1");
    seedInvoice(INVOICE_ID, CUSTOMER_ID, "EUR", "100.00", "0.00");
    seedPayment(PAYMENT_ID, CUSTOMER_ID, "EUR", "100.00", true);
    insertSettlement(SETTLEMENT_ID, CUSTOMER_ID, PAYMENT_ID, "EUR");
    insertAllocation(UUID.randomUUID(), SETTLEMENT_ID, INVOICE_ID, "EUR", "20.00", "ACTIVE", 0);

    ReceivableReconciliationEvidence evidence = load();

    assertTrue(
        evidence
            .issues()
            .contains(ReceivableReconciliationIssue.PAYMENT_REVERSED_WITH_ACTIVE_ALLOCATIONS));
  }

  @Test
  void shouldDetectPaymentCapacityShrinkAcrossAllocationsToDifferentInvoices() {
    seedCustomer(CUSTOMER_ID, "C-1");
    seedInvoice(INVOICE_ID, CUSTOMER_ID, "EUR", "100.00", "0.00");
    seedInvoice(OTHER_INVOICE_ID, CUSTOMER_ID, "EUR", "100.00", "0.00");
    seedPayment(PAYMENT_ID, CUSTOMER_ID, "EUR", "100.00", false);
    insertSettlement(SETTLEMENT_ID, CUSTOMER_ID, PAYMENT_ID, "EUR");
    insertAllocation(UUID.randomUUID(), SETTLEMENT_ID, INVOICE_ID, "EUR", "60.00", "ACTIVE", 0);
    insertAllocation(
        UUID.randomUUID(), SETTLEMENT_ID, OTHER_INVOICE_ID, "EUR", "50.00", "ACTIVE", 1);

    ReceivableReconciliationEvidence evidence = load();

    assertEquals(Money.of("60.00", EUR), evidence.localAllocatedAmount().orElseThrow());
    assertTrue(
        evidence
            .issues()
            .contains(ReceivableReconciliationIssue.PAYMENT_ALLOCATION_CAPACITY_EXCEEDED));
  }

  @Test
  void shouldDetectOtherInvoiceCurrencyConflictOnContributingPayment() {
    seedCustomer(CUSTOMER_ID, "C-1");
    seedInvoice(INVOICE_ID, CUSTOMER_ID, "EUR", "100.00", "0.00");
    seedInvoice(OTHER_INVOICE_ID, CUSTOMER_ID, "USD", "100.00", "0.00");
    seedPayment(PAYMENT_ID, CUSTOMER_ID, "EUR", "100.00", false);
    insertSettlement(SETTLEMENT_ID, CUSTOMER_ID, PAYMENT_ID, "EUR");
    insertAllocation(UUID.randomUUID(), SETTLEMENT_ID, INVOICE_ID, "EUR", "40.00", "ACTIVE", 0);
    insertAllocation(
        UUID.randomUUID(), SETTLEMENT_ID, OTHER_INVOICE_ID, "USD", "10.00", "ACTIVE", 1);

    ReceivableReconciliationEvidence evidence = load();

    assertEquals(Money.of("40.00", EUR), evidence.localAllocatedAmount().orElseThrow());
    assertTrue(
        evidence
            .issues()
            .contains(ReceivableReconciliationIssue.PAYMENT_ALLOCATION_CURRENCY_CONFLICT));
    assertFalse(
        evidence.issues().contains(ReceivableReconciliationIssue.ALLOCATION_CURRENCY_MISMATCH));
  }

  private ReceivableReconciliationEvidence load() {
    return evidenceRepository.load(
        OperationsTenantId.of(TENANT_ID),
        InvoiceId.of(INVOICE_ID),
        BusinessPartnerId.of(CUSTOMER_ID),
        EUR);
  }

  private void seedCustomer(UUID customerId, String number) {
    jdbcTemplate.update(
        """
        INSERT INTO operations_business_partners (
          id, tenant_id, partner_number, display_name, email_address, version, created_at, updated_at
        ) VALUES (?, ?, ?, ?, NULL, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
        """,
        customerId,
        TENANT_ID,
        number,
        "Customer " + number);
  }

  private void seedPayment(
      UUID paymentId, UUID customerId, String currency, String amount, boolean reversed) {
    jdbcTemplate.update(
        """
        INSERT INTO operations_payments (
          id, tenant_id, customer_id, currency_code, amount, payment_date, reversed,
          version, created_at, updated_at
        ) VALUES (?, ?, ?, ?, ?, DATE '2026-08-08', ?, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
        """,
        paymentId,
        TENANT_ID,
        customerId,
        currency,
        new BigDecimal(amount),
        reversed);
  }

  private void seedInvoice(
      UUID invoiceId, UUID customerId, String currency, String originalAmount, String paidAmount) {
    jdbcTemplate.update(
        """
        INSERT INTO operations_invoices (
          id, tenant_id, customer_id, invoice_number, currency_code, original_amount, paid_amount,
          issue_date, due_date, cancelled, version, created_at, updated_at
        ) VALUES (
          ?, ?, ?, ?, ?, ?, ?, DATE '2026-08-01', DATE '2026-08-31', FALSE,
          0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
        )
        """,
        invoiceId,
        TENANT_ID,
        customerId,
        "INV-" + invoiceId.toString().substring(30),
        currency,
        new BigDecimal(originalAmount),
        new BigDecimal(paidAmount));
  }

  private void insertSettlement(
      UUID settlementId, UUID customerId, UUID paymentId, String currency) {
    jdbcTemplate.update(
        """
        INSERT INTO operations_receivable_settlements (
          id, tenant_id, customer_id, payment_id, currency_code, version, created_at, updated_at
        ) VALUES (?, ?, ?, ?, ?, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
        """,
        settlementId,
        TENANT_ID,
        customerId,
        paymentId,
        currency);
  }

  private void insertAllocation(
      UUID allocationId,
      UUID settlementId,
      UUID invoiceId,
      String currency,
      String amount,
      String state,
      int position) {
    jdbcTemplate.update(
        """
        INSERT INTO operations_receivable_allocations (
          id, tenant_id, settlement_id, invoice_id, currency_code, amount, state,
          allocation_position, version, created_at, updated_at
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
        """,
        allocationId,
        TENANT_ID,
        settlementId,
        invoiceId,
        currency,
        new BigDecimal(amount),
        state,
        position);
  }
}
