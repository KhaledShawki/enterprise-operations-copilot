package io.github.khaledshawki.eoc.platform.connectormanagement.adapter.out.persistence;

import io.github.khaledshawki.eoc.connectormanagement.domain.model.ImportCursor;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ImportFailure;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ImportFailureCategory;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ImportMode;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ImportStatus;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ImportType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "connector_import_runs")
class ImportRunJpaEntity {

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @Column(name = "tenant_id", nullable = false, updatable = false)
  private UUID tenantId;

  @Column(name = "connector_id", nullable = false, updatable = false)
  private UUID connectorId;

  @Enumerated(EnumType.STRING)
  @Column(name = "import_type", nullable = false, updatable = false, length = 32)
  private ImportType importType;

  @Enumerated(EnumType.STRING)
  @Column(name = "import_mode", nullable = false, updatable = false, length = 32)
  private ImportMode importMode;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 32)
  private ImportStatus status;

  @Column(name = "committed_cursor", length = ImportCursor.MAX_LENGTH)
  private String committedCursor;

  @Column(name = "fetched_count", nullable = false)
  private long fetchedCount;

  @Column(name = "accepted_count", nullable = false)
  private long acceptedCount;

  @Column(name = "rejected_count", nullable = false)
  private long rejectedCount;

  @Column(name = "duplicate_count", nullable = false)
  private long duplicateCount;

  @Enumerated(EnumType.STRING)
  @Column(name = "failure_category", length = 64)
  private ImportFailureCategory failureCategory;

  @Column(name = "failure_code", length = ImportFailure.MAX_DIAGNOSTIC_CODE_LENGTH)
  private String failureCode;

  @Column(name = "attempt_count", nullable = false)
  private int attemptCount;

  @Column(name = "requested_at", nullable = false, updatable = false)
  private Instant requestedAt;

  @Column(name = "started_at")
  private Instant startedAt;

  @Column(name = "finished_at")
  private Instant finishedAt;

  @Column(name = "next_retry_at")
  private Instant nextRetryAt;

  @Version
  @Column(name = "version", nullable = false)
  private long version;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected ImportRunJpaEntity() {}

  ImportRunJpaEntity(
      UUID id,
      UUID tenantId,
      UUID connectorId,
      ImportType importType,
      ImportMode importMode,
      ImportStatus status,
      String committedCursor,
      long fetchedCount,
      long acceptedCount,
      long rejectedCount,
      long duplicateCount,
      ImportFailureCategory failureCategory,
      String failureCode,
      int attemptCount,
      Instant requestedAt,
      Instant startedAt,
      Instant finishedAt,
      Instant nextRetryAt,
      Instant createdAt,
      Instant updatedAt) {
    this.id = Objects.requireNonNull(id, "Import run id cannot be null");
    this.tenantId = Objects.requireNonNull(tenantId, "Import run tenant id cannot be null");
    this.connectorId = Objects.requireNonNull(connectorId, "Connector id cannot be null");
    this.importType = Objects.requireNonNull(importType, "Import type cannot be null");
    this.importMode = Objects.requireNonNull(importMode, "Import mode cannot be null");
    this.status = Objects.requireNonNull(status, "Import status cannot be null");
    this.committedCursor = committedCursor;
    this.fetchedCount = fetchedCount;
    this.acceptedCount = acceptedCount;
    this.rejectedCount = rejectedCount;
    this.duplicateCount = duplicateCount;
    this.failureCategory = failureCategory;
    this.failureCode = failureCode;
    this.attemptCount = attemptCount;
    this.requestedAt = Objects.requireNonNull(requestedAt, "Request timestamp cannot be null");
    this.startedAt = startedAt;
    this.finishedAt = finishedAt;
    this.nextRetryAt = nextRetryAt;
    this.createdAt = Objects.requireNonNull(createdAt, "Creation timestamp cannot be null");
    this.updatedAt = Objects.requireNonNull(updatedAt, "Update timestamp cannot be null");
  }

  void updateMutableState(
      ImportStatus status,
      String committedCursor,
      long fetchedCount,
      long acceptedCount,
      long rejectedCount,
      long duplicateCount,
      ImportFailureCategory failureCategory,
      String failureCode,
      int attemptCount,
      Instant startedAt,
      Instant finishedAt,
      Instant nextRetryAt,
      Instant updatedAt) {
    this.status = Objects.requireNonNull(status, "Import status cannot be null");
    this.committedCursor = committedCursor;
    this.fetchedCount = fetchedCount;
    this.acceptedCount = acceptedCount;
    this.rejectedCount = rejectedCount;
    this.duplicateCount = duplicateCount;
    this.failureCategory = failureCategory;
    this.failureCode = failureCode;
    this.attemptCount = attemptCount;
    this.startedAt = startedAt;
    this.finishedAt = finishedAt;
    this.nextRetryAt = nextRetryAt;
    this.updatedAt = Objects.requireNonNull(updatedAt, "Update timestamp cannot be null");
  }

  UUID getId() {
    return id;
  }

  UUID getTenantId() {
    return tenantId;
  }

  UUID getConnectorId() {
    return connectorId;
  }

  ImportType getImportType() {
    return importType;
  }

  ImportMode getImportMode() {
    return importMode;
  }

  ImportStatus getStatus() {
    return status;
  }

  String getCommittedCursor() {
    return committedCursor;
  }

  long getFetchedCount() {
    return fetchedCount;
  }

  long getAcceptedCount() {
    return acceptedCount;
  }

  long getRejectedCount() {
    return rejectedCount;
  }

  long getDuplicateCount() {
    return duplicateCount;
  }

  ImportFailureCategory getFailureCategory() {
    return failureCategory;
  }

  String getFailureCode() {
    return failureCode;
  }

  int getAttemptCount() {
    return attemptCount;
  }

  Instant getRequestedAt() {
    return requestedAt;
  }

  Instant getStartedAt() {
    return startedAt;
  }

  Instant getFinishedAt() {
    return finishedAt;
  }

  Instant getNextRetryAt() {
    return nextRetryAt;
  }

  long getVersion() {
    return version;
  }

  Instant getCreatedAt() {
    return createdAt;
  }

  Instant getUpdatedAt() {
    return updatedAt;
  }
}
