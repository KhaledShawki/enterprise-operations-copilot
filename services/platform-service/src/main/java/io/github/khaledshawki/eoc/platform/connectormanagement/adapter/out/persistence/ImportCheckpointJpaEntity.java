package io.github.khaledshawki.eoc.platform.connectormanagement.adapter.out.persistence;

import io.github.khaledshawki.eoc.connectormanagement.domain.model.ImportCursor;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "connector_import_checkpoints")
@IdClass(ImportCheckpointJpaId.class)
class ImportCheckpointJpaEntity {

  @Id
  @Column(name = "tenant_id", nullable = false, updatable = false)
  private UUID tenantId;

  @Id
  @Column(name = "connector_id", nullable = false, updatable = false)
  private UUID connectorId;

  @Id
  @Column(name = "import_type", nullable = false, updatable = false, length = 32)
  private String importType;

  @Column(name = "committed_cursor", nullable = false, length = ImportCursor.MAX_LENGTH)
  private String committedCursor;

  @Column(name = "last_import_run_id", nullable = false)
  private UUID lastImportRunId;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected ImportCheckpointJpaEntity() {}

  ImportCheckpointJpaEntity(
      UUID tenantId,
      UUID connectorId,
      String importType,
      String committedCursor,
      UUID lastImportRunId,
      Instant updatedAt) {
    this.tenantId = Objects.requireNonNull(tenantId, "Checkpoint tenant id cannot be null");
    this.connectorId =
        Objects.requireNonNull(connectorId, "Checkpoint connector id cannot be null");
    this.importType = Objects.requireNonNull(importType, "Checkpoint import type cannot be null");
    update(committedCursor, lastImportRunId, updatedAt);
  }

  void update(String committedCursor, UUID lastImportRunId, Instant updatedAt) {
    this.committedCursor =
        Objects.requireNonNull(committedCursor, "Committed cursor cannot be null");
    this.lastImportRunId =
        Objects.requireNonNull(lastImportRunId, "Last import run id cannot be null");
    this.updatedAt = Objects.requireNonNull(updatedAt, "Checkpoint timestamp cannot be null");
  }

  String getCommittedCursor() {
    return committedCursor;
  }
}
