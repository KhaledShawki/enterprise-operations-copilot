package io.github.khaledshawki.eoc.platform.operations.adapter.out.persistence;

import io.github.khaledshawki.eoc.operations.application.exception.ConcurrentPaymentImportException;
import io.github.khaledshawki.eoc.operations.application.model.importing.PaymentImportReceipt;
import io.github.khaledshawki.eoc.operations.application.port.in.PaymentImportResult;
import io.github.khaledshawki.eoc.operations.application.port.out.PaymentImportReceiptRepository;
import io.github.khaledshawki.eoc.operations.domain.model.OperationsTenantId;
import io.github.khaledshawki.eoc.operations.domain.model.SourceSystemId;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
class PaymentImportReceiptPersistenceAdapter implements PaymentImportReceiptRepository {

  private final SpringDataPaymentImportReceiptRepository receiptRepository;
  private final EntityManager entityManager;

  PaymentImportReceiptPersistenceAdapter(
      SpringDataPaymentImportReceiptRepository receiptRepository, EntityManager entityManager) {
    this.receiptRepository =
        Objects.requireNonNull(receiptRepository, "Payment receipt repository cannot be null");
    this.entityManager = Objects.requireNonNull(entityManager, "Entity manager cannot be null");
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<PaymentImportReceipt> find(
      OperationsTenantId tenantId,
      SourceSystemId sourceSystemId,
      UUID importBatchId,
      UUID pageAcceptanceId) {
    Objects.requireNonNull(tenantId, "Payment receipt tenant id cannot be null");
    Objects.requireNonNull(sourceSystemId, "Payment receipt source system id cannot be null");
    Objects.requireNonNull(importBatchId, "Payment receipt batch id cannot be null");
    Objects.requireNonNull(pageAcceptanceId, "Payment receipt acceptance id cannot be null");
    return receiptRepository
        .findById(
            new PaymentImportReceiptJpaId(
                tenantId.value(), sourceSystemId.value(), importBatchId, pageAcceptanceId))
        .map(PaymentImportReceiptPersistenceAdapter::toReceipt);
  }

  @Override
  @Transactional
  public PaymentImportReceipt save(
      OperationsTenantId tenantId,
      SourceSystemId sourceSystemId,
      UUID importBatchId,
      PaymentImportReceipt receipt) {
    Objects.requireNonNull(tenantId, "Payment receipt tenant id cannot be null");
    Objects.requireNonNull(sourceSystemId, "Payment receipt source system id cannot be null");
    Objects.requireNonNull(importBatchId, "Payment receipt batch id cannot be null");
    Objects.requireNonNull(receipt, "Payment import receipt cannot be null");
    PaymentImportResult result = receipt.result();
    PaymentImportReceiptJpaEntity entity =
        new PaymentImportReceiptJpaEntity(
            tenantId.value(),
            sourceSystemId.value(),
            importBatchId,
            result.pageAcceptanceId(),
            receipt.payloadFingerprint(),
            result.fetchedCount(),
            result.createdCount(),
            result.updatedCount(),
            result.duplicateCount(),
            result.staleCount(),
            result.acceptedAt());
    try {
      entityManager.persist(entity);
      entityManager.flush();
      return toReceipt(entity);
    } catch (DataIntegrityViolationException | PersistenceException exception) {
      throw new ConcurrentPaymentImportException(
          "Payment import page was accepted concurrently", exception);
    }
  }

  private static PaymentImportReceipt toReceipt(PaymentImportReceiptJpaEntity entity) {
    return new PaymentImportReceipt(
        entity.getPayloadFingerprint(),
        new PaymentImportResult(
            entity.getPageAcceptanceId(),
            entity.getFetchedCount(),
            entity.getCreatedCount(),
            entity.getUpdatedCount(),
            entity.getDuplicateCount(),
            entity.getStaleCount(),
            entity.getAcceptedAt()));
  }
}
