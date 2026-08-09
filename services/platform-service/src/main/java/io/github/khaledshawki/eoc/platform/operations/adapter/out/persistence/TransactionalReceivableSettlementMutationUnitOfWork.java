package io.github.khaledshawki.eoc.platform.operations.adapter.out.persistence;

import io.github.khaledshawki.eoc.operations.application.model.settlement.ReceivableAllocationResult;
import io.github.khaledshawki.eoc.operations.application.port.out.ReceivableSettlementMutationUnitOfWork;
import io.github.khaledshawki.eoc.operations.domain.model.InvoiceId;
import io.github.khaledshawki.eoc.operations.domain.model.OperationsTenantId;
import io.github.khaledshawki.eoc.operations.domain.model.PaymentId;
import io.github.khaledshawki.eoc.operations.domain.model.ReceivableAllocationId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Component
class TransactionalReceivableSettlementMutationUnitOfWork
    implements ReceivableSettlementMutationUnitOfWork {

  private static final String ENSURE_LOCK_SQL =
      """
      INSERT INTO operations_receivable_settlement_locks (tenant_id, resource_kind, resource_id)
      VALUES (?, ?, ?)
      ON CONFLICT (tenant_id, resource_kind, resource_id) DO NOTHING
      """;

  private static final String ACQUIRE_LOCK_SQL =
      """
      SELECT resource_id
      FROM operations_receivable_settlement_locks
      WHERE tenant_id = ?
        AND resource_kind = ?
        AND resource_id = ?
      FOR UPDATE
      """;

  private static final Comparator<LockKey> LOCK_ORDER =
      Comparator.comparing((LockKey key) -> key.kind().ordinal())
          .thenComparing(key -> key.resourceId().toString());

  private final JdbcTemplate jdbcTemplate;
  private final TransactionTemplate transactionTemplate;

  TransactionalReceivableSettlementMutationUnitOfWork(
      JdbcTemplate jdbcTemplate, PlatformTransactionManager transactionManager) {
    this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "JDBC template cannot be null");
    this.transactionTemplate =
        new TransactionTemplate(
            Objects.requireNonNull(transactionManager, "Transaction manager cannot be null"));
  }

  @Override
  public ReceivableAllocationResult execute(
      OperationsTenantId tenantId,
      PaymentId paymentId,
      InvoiceId invoiceId,
      ReceivableAllocationId allocationId,
      Supplier<ReceivableAllocationResult> work) {
    Objects.requireNonNull(tenantId, "Receivable settlement tenant id cannot be null");
    Objects.requireNonNull(paymentId, "Receivable settlement payment id cannot be null");
    Objects.requireNonNull(invoiceId, "Receivable allocation invoice id cannot be null");
    Objects.requireNonNull(allocationId, "Receivable allocation id cannot be null");
    Objects.requireNonNull(work, "Receivable settlement mutation work cannot be null");

    ReceivableAllocationResult result =
        transactionTemplate.execute(
            status -> {
              acquireLocks(tenantId, paymentId, invoiceId, allocationId);
              return work.get();
            });
    return Objects.requireNonNull(result, "Receivable settlement transaction returned null");
  }

  private void acquireLocks(
      OperationsTenantId tenantId,
      PaymentId paymentId,
      InvoiceId invoiceId,
      ReceivableAllocationId allocationId) {
    ArrayList<LockKey> keys =
        new ArrayList<>(
            List.of(
                new LockKey(LockKind.PAYMENT, paymentId.value()),
                new LockKey(LockKind.INVOICE, invoiceId.value()),
                new LockKey(LockKind.ALLOCATION, allocationId.value())));
    keys.sort(LOCK_ORDER);
    for (LockKey key : keys) {
      ensureLockRow(tenantId, key);
      acquireLockRow(tenantId, key);
    }
  }

  private void ensureLockRow(OperationsTenantId tenantId, LockKey key) {
    jdbcTemplate.update(ENSURE_LOCK_SQL, tenantId.value(), key.kind().name(), key.resourceId());
  }

  private void acquireLockRow(OperationsTenantId tenantId, LockKey key) {
    UUID locked =
        jdbcTemplate.queryForObject(
            ACQUIRE_LOCK_SQL, UUID.class, tenantId.value(), key.kind().name(), key.resourceId());
    if (!key.resourceId().equals(locked)) {
      throw new IllegalStateException(
          "Receivable settlement coordination lock could not be acquired");
    }
  }

  private enum LockKind {
    ALLOCATION,
    INVOICE,
    PAYMENT
  }

  private record LockKey(LockKind kind, UUID resourceId) {
    private LockKey {
      Objects.requireNonNull(kind, "Receivable settlement lock kind cannot be null");
      Objects.requireNonNull(resourceId, "Receivable settlement lock resource id cannot be null");
    }
  }
}
