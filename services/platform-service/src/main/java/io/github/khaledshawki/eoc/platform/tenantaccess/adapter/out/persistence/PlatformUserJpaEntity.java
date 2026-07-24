package io.github.khaledshawki.eoc.platform.tenantaccess.adapter.out.persistence;

import io.github.khaledshawki.eoc.tenantaccess.domain.model.ExternalIdentity;
import io.github.khaledshawki.eoc.tenantaccess.domain.model.PlatformUserStatus;
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
@Table(name = "platform_users")
class PlatformUserJpaEntity {

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @Column(
      name = "issuer",
      nullable = false,
      updatable = false,
      length = ExternalIdentity.MAX_ISSUER_LENGTH)
  private String issuer;

  @Column(
      name = "subject",
      nullable = false,
      updatable = false,
      length = ExternalIdentity.MAX_SUBJECT_LENGTH)
  private String subject;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 32)
  private PlatformUserStatus status;

  @Version
  @Column(name = "version", nullable = false)
  private long version;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected PlatformUserJpaEntity() {}

  PlatformUserJpaEntity(
      UUID id,
      String issuer,
      String subject,
      PlatformUserStatus status,
      Instant createdAt,
      Instant updatedAt) {
    this.id = id;
    this.issuer = issuer;
    this.subject = subject;
    this.status = status;
    this.createdAt = createdAt;
    this.updatedAt = updatedAt;
  }

  void updateMutableState(PlatformUserStatus status, Instant updatedAt) {
    this.status = Objects.requireNonNull(status, "Platform user status cannot be null");
    this.updatedAt = Objects.requireNonNull(updatedAt, "Updated at cannot be null");
  }

  UUID getId() {
    return id;
  }

  String getIssuer() {
    return issuer;
  }

  String getSubject() {
    return subject;
  }

  PlatformUserStatus getStatus() {
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
