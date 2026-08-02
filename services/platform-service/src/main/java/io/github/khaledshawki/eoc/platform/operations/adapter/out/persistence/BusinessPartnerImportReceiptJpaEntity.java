package io.github.khaledshawki.eoc.platform.operations.adapter.out.persistence;

import io.github.khaledshawki.eoc.operations.application.model.importing.BusinessPartnerImportReceipt;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "operations_business_partner_import_receipts")
@IdClass(BusinessPartnerImportReceiptJpaId.class)
class BusinessPartnerImportReceiptJpaEntity {

  @Id
  @Column(name = "tenant_id", nullable = false, updatable = false)
  private UUID tenantId;

  @Id
  @Column(name = "source_system_id", nullable = false, updatable = false)
  private UUID sourceSystemId;

  @Id
  @Column(name = "import_batch_id", nullable = false, updatable = false)
  private UUID importBatchId;

  @Id
  @Column(name = "page_acceptance_id", nullable = false, updatable = false)
  private UUID pageAcceptanceId;

  @Column(
      name = "payload_fingerprint",
      nullable = false,
      updatable = false,
      length = BusinessPartnerImportReceipt.SHA_256_HEX_LENGTH)
  private String payloadFingerprint;

  @Column(name = "fetched_count", nullable = false, updatable = false)
  private long fetchedCount;

  @Column(name = "created_count", nullable = false, updatable = false)
  private long createdCount;

  @Column(name = "updated_count", nullable = false, updatable = false)
  private long updatedCount;

  @Column(name = "duplicate_count", nullable = false, updatable = false)
  private long duplicateCount;

  @Column(name = "stale_count", nullable = false, updatable = false)
  private long staleCount;

  @Column(name = "accepted_at", nullable = false, updatable = false)
  private Instant acceptedAt;

  protected BusinessPartnerImportReceiptJpaEntity() {}

  BusinessPartnerImportReceiptJpaEntity(
      UUID tenantId,
      UUID sourceSystemId,
      UUID importBatchId,
      UUID pageAcceptanceId,
      String payloadFingerprint,
      long fetchedCount,
      long createdCount,
      long updatedCount,
      long duplicateCount,
      long staleCount,
      Instant acceptedAt) {
    this.tenantId = Objects.requireNonNull(tenantId, "Import receipt tenant id cannot be null");
    this.sourceSystemId =
        Objects.requireNonNull(sourceSystemId, "Import receipt source system id cannot be null");
    this.importBatchId =
        Objects.requireNonNull(importBatchId, "Import receipt batch id cannot be null");
    this.pageAcceptanceId =
        Objects.requireNonNull(pageAcceptanceId, "Import receipt acceptance id cannot be null");
    this.payloadFingerprint =
        Objects.requireNonNull(payloadFingerprint, "Import payload fingerprint cannot be null");
    this.fetchedCount = fetchedCount;
    this.createdCount = createdCount;
    this.updatedCount = updatedCount;
    this.duplicateCount = duplicateCount;
    this.staleCount = staleCount;
    this.acceptedAt = Objects.requireNonNull(acceptedAt, "Acceptance timestamp cannot be null");
  }

  UUID getTenantId() {
    return tenantId;
  }

  UUID getSourceSystemId() {
    return sourceSystemId;
  }

  UUID getImportBatchId() {
    return importBatchId;
  }

  UUID getPageAcceptanceId() {
    return pageAcceptanceId;
  }

  String getPayloadFingerprint() {
    return payloadFingerprint;
  }

  long getFetchedCount() {
    return fetchedCount;
  }

  long getCreatedCount() {
    return createdCount;
  }

  long getUpdatedCount() {
    return updatedCount;
  }

  long getDuplicateCount() {
    return duplicateCount;
  }

  long getStaleCount() {
    return staleCount;
  }

  Instant getAcceptedAt() {
    return acceptedAt;
  }
}
