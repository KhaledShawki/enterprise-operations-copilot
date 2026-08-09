package io.github.khaledshawki.eoc.platform.operations.adapter.out.persistence;

import io.github.khaledshawki.eoc.operations.application.exception.ReceivableReconciliationStateCorruptedException;
import io.github.khaledshawki.eoc.operations.application.model.reconciliation.ReceivableReconciliationEvidence;
import io.github.khaledshawki.eoc.operations.application.model.reconciliation.ReceivableReconciliationIssue;
import io.github.khaledshawki.eoc.operations.application.port.out.ReceivableReconciliationEvidenceRepository;
import io.github.khaledshawki.eoc.operations.domain.model.BusinessPartnerId;
import io.github.khaledshawki.eoc.operations.domain.model.CurrencyCode;
import io.github.khaledshawki.eoc.operations.domain.model.InvoiceId;
import io.github.khaledshawki.eoc.operations.domain.model.Money;
import io.github.khaledshawki.eoc.operations.domain.model.OperationsTenantId;
import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
class ReceivableReconciliationEvidencePersistenceAdapter
    implements ReceivableReconciliationEvidenceRepository {

  private static final String EVIDENCE_SQL =
      """
      WITH target_active AS (
        SELECT
          a.amount,
          a.currency_code AS allocation_currency,
          s.customer_id AS settlement_customer_id,
          s.currency_code AS settlement_currency,
          s.payment_id,
          p.customer_id AS payment_customer_id,
          p.currency_code AS payment_currency,
          p.amount AS payment_amount,
          p.reversed
        FROM operations_receivable_allocations a
        JOIN operations_receivable_settlements s
          ON s.tenant_id = a.tenant_id
         AND s.id = a.settlement_id
        JOIN operations_payments p
          ON p.tenant_id = s.tenant_id
         AND p.id = s.payment_id
        WHERE a.tenant_id = ?
          AND a.invoice_id = ?
          AND a.state = 'ACTIVE'
      ),
      target_payments AS (
        SELECT DISTINCT payment_id
        FROM target_active
      ),
      payment_totals AS (
        SELECT
          s.payment_id,
          p.amount AS payment_amount,
          p.currency_code AS payment_currency,
          SUM(
            CASE
              WHEN a.currency_code = p.currency_code THEN a.amount
              ELSE 0
            END
          ) AS comparable_allocated_amount,
          BOOL_OR(a.currency_code <> p.currency_code) AS allocation_currency_mismatch
        FROM operations_receivable_allocations a
        JOIN operations_receivable_settlements s
          ON s.tenant_id = a.tenant_id
         AND s.id = a.settlement_id
        JOIN operations_payments p
          ON p.tenant_id = s.tenant_id
         AND p.id = s.payment_id
        JOIN target_payments target
          ON target.payment_id = s.payment_id
        WHERE a.tenant_id = ?
          AND a.state = 'ACTIVE'
        GROUP BY s.payment_id, p.amount, p.currency_code
      )
      SELECT
        COUNT(*) AS active_count,
        COALESCE(
          SUM(
            CASE
              WHEN allocation_currency = ? THEN amount
              ELSE 0
            END
          ),
          0
        ) AS matching_currency_total,
        COALESCE(BOOL_OR(allocation_currency <> ?), FALSE)
          AS allocation_currency_mismatch,
        COALESCE(BOOL_OR(settlement_customer_id <> ?), FALSE)
          AS settlement_customer_mismatch,
        COALESCE(BOOL_OR(settlement_currency <> ?), FALSE)
          AS settlement_currency_mismatch,
        COALESCE(BOOL_OR(payment_customer_id <> ?), FALSE)
          AS payment_customer_mismatch,
        COALESCE(BOOL_OR(payment_currency <> ?), FALSE)
          AS payment_currency_mismatch,
        COALESCE(BOOL_OR(reversed), FALSE)
          AS payment_reversed,
        COALESCE(
          (
            SELECT BOOL_OR(allocation_currency_mismatch)
            FROM payment_totals
          ),
          FALSE
        ) AS payment_allocation_currency_conflict,
        COALESCE(
          (
            SELECT BOOL_OR(
              NOT allocation_currency_mismatch
              AND comparable_allocated_amount > payment_amount
            )
            FROM payment_totals
          ),
          FALSE
        ) AS payment_capacity_exceeded
      FROM target_active
      """;

  private final JdbcTemplate jdbcTemplate;

  ReceivableReconciliationEvidencePersistenceAdapter(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate =
        Objects.requireNonNull(
            jdbcTemplate, "Receivable reconciliation JDBC template cannot be null");
  }

  @Override
  @Transactional(readOnly = true)
  public ReceivableReconciliationEvidence load(
      OperationsTenantId tenantId,
      InvoiceId invoiceId,
      BusinessPartnerId expectedCustomerId,
      CurrencyCode expectedCurrency) {
    Objects.requireNonNull(tenantId, "Receivable reconciliation tenant id cannot be null");
    Objects.requireNonNull(invoiceId, "Receivable reconciliation Invoice id cannot be null");
    Objects.requireNonNull(
        expectedCustomerId, "Receivable reconciliation expected customer id cannot be null");
    Objects.requireNonNull(
        expectedCurrency, "Receivable reconciliation expected currency cannot be null");

    EvidenceRow row =
        jdbcTemplate.queryForObject(
            EVIDENCE_SQL,
            (resultSet, rowNumber) ->
                new EvidenceRow(
                    resultSet.getLong("active_count"),
                    resultSet.getBigDecimal("matching_currency_total"),
                    resultSet.getBoolean("allocation_currency_mismatch"),
                    resultSet.getBoolean("settlement_customer_mismatch"),
                    resultSet.getBoolean("settlement_currency_mismatch"),
                    resultSet.getBoolean("payment_customer_mismatch"),
                    resultSet.getBoolean("payment_currency_mismatch"),
                    resultSet.getBoolean("payment_reversed"),
                    resultSet.getBoolean("payment_allocation_currency_conflict"),
                    resultSet.getBoolean("payment_capacity_exceeded")),
            tenantId.value(),
            invoiceId.value(),
            tenantId.value(),
            expectedCurrency.value(),
            expectedCurrency.value(),
            expectedCustomerId.value(),
            expectedCurrency.value(),
            expectedCustomerId.value(),
            expectedCurrency.value());
    if (row == null) {
      throw corrupted("Receivable reconciliation evidence query returned null");
    }

    EnumSet<ReceivableReconciliationIssue> issues =
        EnumSet.noneOf(ReceivableReconciliationIssue.class);
    addIf(
        issues,
        row.allocationCurrencyMismatch(),
        ReceivableReconciliationIssue.ALLOCATION_CURRENCY_MISMATCH);
    addIf(
        issues,
        row.settlementCustomerMismatch(),
        ReceivableReconciliationIssue.SETTLEMENT_CUSTOMER_MISMATCH);
    addIf(
        issues,
        row.settlementCurrencyMismatch(),
        ReceivableReconciliationIssue.SETTLEMENT_CURRENCY_MISMATCH);
    addIf(
        issues,
        row.paymentCustomerMismatch(),
        ReceivableReconciliationIssue.PAYMENT_CUSTOMER_MISMATCH);
    addIf(
        issues,
        row.paymentCurrencyMismatch(),
        ReceivableReconciliationIssue.PAYMENT_CURRENCY_MISMATCH);
    addIf(
        issues,
        row.paymentReversed(),
        ReceivableReconciliationIssue.PAYMENT_REVERSED_WITH_ACTIVE_ALLOCATIONS);
    addIf(
        issues,
        row.paymentAllocationCurrencyConflict(),
        ReceivableReconciliationIssue.PAYMENT_ALLOCATION_CURRENCY_CONFLICT);
    addIf(
        issues,
        row.paymentCapacityExceeded(),
        ReceivableReconciliationIssue.PAYMENT_ALLOCATION_CAPACITY_EXCEEDED);

    Optional<Money> localAllocatedAmount;
    if (row.allocationCurrencyMismatch()) {
      localAllocatedAmount = Optional.empty();
    } else {
      if (row.matchingCurrencyTotal() == null) {
        throw corrupted("Receivable reconciliation local allocation total cannot be null");
      }
      try {
        localAllocatedAmount =
            Optional.of(new Money(row.matchingCurrencyTotal(), expectedCurrency));
      } catch (RuntimeException exception) {
        throw corrupted(
            "Receivable reconciliation local allocation total cannot be represented as Money",
            exception);
      }
    }

    try {
      return new ReceivableReconciliationEvidence(localAllocatedAmount, row.activeCount(), issues);
    } catch (IllegalArgumentException exception) {
      throw corrupted("Receivable reconciliation persistence evidence is inconsistent", exception);
    }
  }

  private static void addIf(
      EnumSet<ReceivableReconciliationIssue> issues,
      boolean condition,
      ReceivableReconciliationIssue issue) {
    if (condition) {
      issues.add(issue);
    }
  }

  private static ReceivableReconciliationStateCorruptedException corrupted(String detail) {
    return new ReceivableReconciliationStateCorruptedException(detail);
  }

  private static ReceivableReconciliationStateCorruptedException corrupted(
      String detail, RuntimeException cause) {
    return new ReceivableReconciliationStateCorruptedException(detail, cause);
  }

  private record EvidenceRow(
      long activeCount,
      BigDecimal matchingCurrencyTotal,
      boolean allocationCurrencyMismatch,
      boolean settlementCustomerMismatch,
      boolean settlementCurrencyMismatch,
      boolean paymentCustomerMismatch,
      boolean paymentCurrencyMismatch,
      boolean paymentReversed,
      boolean paymentAllocationCurrencyConflict,
      boolean paymentCapacityExceeded) {}
}
