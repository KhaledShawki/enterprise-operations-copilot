package io.github.khaledshawki.eoc.platform.operations.adapter.out.persistence;

import io.github.khaledshawki.eoc.operations.application.exception.ConcurrentInvoiceImportException;
import io.github.khaledshawki.eoc.operations.application.model.importing.InvoiceImportReceipt;
import io.github.khaledshawki.eoc.operations.application.port.in.InvoiceImportResult;
import io.github.khaledshawki.eoc.operations.application.port.out.InvoiceImportReceiptRepository;
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
class InvoiceImportReceiptPersistenceAdapter implements InvoiceImportReceiptRepository {

  private final SpringDataInvoiceImportReceiptRepository receiptRepository;
  private final EntityManager entityManager;

  InvoiceImportReceiptPersistenceAdapter(
      SpringDataInvoiceImportReceiptRepository receiptRepository, EntityManager entityManager) {
    this.receiptRepository =
        Objects.requireNonNull(receiptRepository, "Invoice receipt repository cannot be null");
    this.entityManager = Objects.requireNonNull(entityManager, "Entity manager cannot be null");
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<InvoiceImportReceipt> find(
      OperationsTenantId tenantId,
      SourceSystemId sourceSystemId,
      UUID importBatchId,
      UUID pageAcceptanceId) {
    Objects.requireNonNull(tenantId, "Invoice receipt tenant id cannot be null");
    Objects.requireNonNull(sourceSystemId, "Invoice receipt source system id cannot be null");
    Objects.requireNonNull(importBatchId, "Invoice receipt batch id cannot be null");
    Objects.requireNonNull(pageAcceptanceId, "Invoice receipt acceptance id cannot be null");
    return receiptRepository
        .findById(
            new InvoiceImportReceiptJpaId(
                tenantId.value(), sourceSystemId.value(), importBatchId, pageAcceptanceId))
        .map(InvoiceImportReceiptPersistenceAdapter::toReceipt);
  }

  @Override
  @Transactional
  public InvoiceImportReceipt save(
      OperationsTenantId tenantId,
      SourceSystemId sourceSystemId,
      UUID importBatchId,
      InvoiceImportReceipt receipt) {
    Objects.requireNonNull(tenantId, "Invoice receipt tenant id cannot be null");
    Objects.requireNonNull(sourceSystemId, "Invoice receipt source system id cannot be null");
    Objects.requireNonNull(importBatchId, "Invoice receipt batch id cannot be null");
    Objects.requireNonNull(receipt, "Invoice import receipt cannot be null");
    InvoiceImportResult result = receipt.result();
    InvoiceImportReceiptJpaEntity entity =
        new InvoiceImportReceiptJpaEntity(
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
      throw new ConcurrentInvoiceImportException(
          "Invoice import page was accepted concurrently", exception);
    }
  }

  private static InvoiceImportReceipt toReceipt(InvoiceImportReceiptJpaEntity entity) {
    return new InvoiceImportReceipt(
        entity.getPayloadFingerprint(),
        new InvoiceImportResult(
            entity.getPageAcceptanceId(),
            entity.getFetchedCount(),
            entity.getCreatedCount(),
            entity.getUpdatedCount(),
            entity.getDuplicateCount(),
            entity.getStaleCount(),
            entity.getAcceptedAt()));
  }
}
