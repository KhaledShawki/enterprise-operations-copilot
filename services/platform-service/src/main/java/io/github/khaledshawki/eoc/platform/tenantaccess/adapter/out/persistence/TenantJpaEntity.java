package io.github.khaledshawki.eoc.platform.tenantaccess.adapter.out.persistence;

import io.github.khaledshawki.eoc.tenantaccess.domain.model.TenantStatus;
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
@Table(name = "tenants")
class TenantJpaEntity {

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @Column(name = "tenant_key", nullable = false, length = 63)
  private String tenantKey;

  @Column(name = "display_name", nullable = false, length = 100)
  private String displayName;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 32)
  private TenantStatus status;

  @Version
  @Column(name = "version", nullable = false)
  private long version;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected TenantJpaEntity() {}

  TenantJpaEntity(
      UUID id,
      String tenantKey,
      String displayName,
      TenantStatus status,
      Instant createdAt,
      Instant updatedAt) {
    this.id = id;
    this.tenantKey = tenantKey;
    this.displayName = displayName;
    this.status = status;
    this.createdAt = createdAt;
    this.updatedAt = updatedAt;
  }

  void updateMutableState(String displayName, TenantStatus status, Instant updatedAt) {
    this.displayName = Objects.requireNonNull(displayName, "Display name cannot be null");
    this.status = Objects.requireNonNull(status, "Status cannot be null");
    this.updatedAt = Objects.requireNonNull(updatedAt, "Updated at cannot be null");
  }

  UUID getId() {
    return id;
  }

  String getTenantKey() {
    return tenantKey;
  }

  String getDisplayName() {
    return displayName;
  }

  TenantStatus getStatus() {
    return status;
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
