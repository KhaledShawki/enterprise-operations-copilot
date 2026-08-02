package io.github.khaledshawki.eoc.platform.operations.adapter.out.persistence;

import io.github.khaledshawki.eoc.operations.application.exception.ConcurrentBusinessPartnerImportException;
import io.github.khaledshawki.eoc.operations.application.model.importing.BusinessPartnerImportReceipt;
import io.github.khaledshawki.eoc.operations.application.port.in.BusinessPartnerImportResult;
import io.github.khaledshawki.eoc.operations.application.port.out.BusinessPartnerImportReceiptRepository;
import io.github.khaledshawki.eoc.operations.domain.model.OperationsTenantId;
import io.github.khaledshawki.eoc.operations.domain.model.SourceSystemId;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
class BusinessPartnerImportReceiptPersistenceAdapter
    implements BusinessPartnerImportReceiptRepository {

  private final SpringDataBusinessPartnerImportReceiptRepository receiptRepository;

  BusinessPartnerImportReceiptPersistenceAdapter(
      SpringDataBusinessPartnerImportReceiptRepository receiptRepository) {
    this.receiptRepository =
        Objects.requireNonNull(
            receiptRepository, "Business partner receipt repository cannot be null");
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<BusinessPartnerImportReceipt> find(
      OperationsTenantId tenantId,
      SourceSystemId sourceSystemId,
      UUID importBatchId,
      UUID pageAcceptanceId) {
    Objects.requireNonNull(tenantId, "Import receipt tenant id cannot be null");
    Objects.requireNonNull(sourceSystemId, "Import receipt source system id cannot be null");
    Objects.requireNonNull(importBatchId, "Import receipt batch id cannot be null");
    Objects.requireNonNull(pageAcceptanceId, "Import receipt acceptance id cannot be null");
    return receiptRepository
        .findById(
            new BusinessPartnerImportReceiptJpaId(
                tenantId.value(), sourceSystemId.value(), importBatchId, pageAcceptanceId))
        .map(BusinessPartnerImportReceiptPersistenceAdapter::toReceipt);
  }

  @Override
  @Transactional
  public BusinessPartnerImportReceipt save(
      OperationsTenantId tenantId,
      SourceSystemId sourceSystemId,
      UUID importBatchId,
      BusinessPartnerImportReceipt receipt) {
    Objects.requireNonNull(tenantId, "Import receipt tenant id cannot be null");
    Objects.requireNonNull(sourceSystemId, "Import receipt source system id cannot be null");
    Objects.requireNonNull(importBatchId, "Import receipt batch id cannot be null");
    Objects.requireNonNull(receipt, "Business partner import receipt cannot be null");
    BusinessPartnerImportResult result = receipt.result();
    BusinessPartnerImportReceiptJpaEntity entity =
        new BusinessPartnerImportReceiptJpaEntity(
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
      return toReceipt(receiptRepository.saveAndFlush(entity));
    } catch (DataIntegrityViolationException exception) {
      throw new ConcurrentBusinessPartnerImportException(
          "Business partner import page was accepted concurrently", exception);
    }
  }

  private static BusinessPartnerImportReceipt toReceipt(
      BusinessPartnerImportReceiptJpaEntity entity) {
    return new BusinessPartnerImportReceipt(
        entity.getPayloadFingerprint(),
        new BusinessPartnerImportResult(
            entity.getPageAcceptanceId(),
            entity.getFetchedCount(),
            entity.getCreatedCount(),
            entity.getUpdatedCount(),
            entity.getDuplicateCount(),
            entity.getStaleCount(),
            entity.getAcceptedAt()));
  }
}
