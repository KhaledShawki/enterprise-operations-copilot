package io.github.khaledshawki.eoc.platform.connectormanagement.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "connector_import_page_acceptances")
@IdClass(ImportPageAcceptanceJpaId.class)
class ImportPageAcceptanceJpaEntity {

  @Id
  @Column(name = "import_run_id", nullable = false, updatable = false)
  private UUID importRunId;

  @Id
  @Column(name = "acceptance_id", nullable = false, updatable = false)
  private UUID acceptanceId;

  @Column(name = "accepted_at", nullable = false, updatable = false)
  private Instant acceptedAt;

  protected ImportPageAcceptanceJpaEntity() {}

  ImportPageAcceptanceJpaEntity(UUID importRunId, UUID acceptanceId, Instant acceptedAt) {
    this.importRunId = Objects.requireNonNull(importRunId, "Import run id cannot be null");
    this.acceptanceId = Objects.requireNonNull(acceptanceId, "Acceptance id cannot be null");
    this.acceptedAt = Objects.requireNonNull(acceptedAt, "Acceptance timestamp cannot be null");
  }
}
