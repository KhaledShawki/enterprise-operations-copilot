package io.github.khaledshawki.eoc.platform.operations.adapter.out.persistence;

import io.github.khaledshawki.eoc.operations.domain.model.SourceRecordFingerprint;
import io.github.khaledshawki.eoc.operations.domain.model.SourceRecordIdentity;
import io.github.khaledshawki.eoc.operations.domain.model.SourceRecordVersion;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "operations_payment_source_mappings")
@IdClass(PaymentSourceMappingJpaId.class)
class PaymentSourceMappingJpaEntity {

  @Id
  @Column(name = "tenant_id", nullable = false, updatable = false)
  private UUID tenantId;

  @Id
  @Column(name = "source_system_id", nullable = false, updatable = false)
  private UUID sourceSystemId;

  @Id
  @Enumerated(EnumType.STRING)
  @Column(name = "source_identity_kind", nullable = false, updatable = false, length = 32)
  private SourceRecordIdentity.Kind sourceIdentityKind;

  @Id
  @Column(
      name = "source_identity_value",
      nullable = false,
      updatable = false,
      length = SourceRecordIdentity.MAX_VALUE_LENGTH)
  private String sourceIdentityValue;

  @Column(name = "payment_id", nullable = false, updatable = false)
  private UUID paymentId;

  @Column(name = "source_version", nullable = false, length = SourceRecordVersion.MAX_LENGTH)
  private String sourceVersion;

  @Column(name = "source_modified_at")
  private Instant sourceModifiedAt;

  @Column(
      name = "payload_fingerprint",
      nullable = false,
      length = SourceRecordFingerprint.SHA_256_HEX_LENGTH)
  private String payloadFingerprint;

  @Version
  @Column(name = "version", nullable = false)
  private Long version;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected PaymentSourceMappingJpaEntity() {}

  PaymentSourceMappingJpaEntity(
      UUID tenantId,
      UUID sourceSystemId,
      SourceRecordIdentity.Kind sourceIdentityKind,
      String sourceIdentityValue,
      UUID paymentId,
      String sourceVersion,
      Instant sourceModifiedAt,
      String payloadFingerprint,
      Instant createdAt,
      Instant updatedAt) {
    this.tenantId =
        Objects.requireNonNull(tenantId, "Payment source mapping tenant id cannot be null");
    this.sourceSystemId =
        Objects.requireNonNull(sourceSystemId, "Payment source mapping system id cannot be null");
    this.sourceIdentityKind =
        Objects.requireNonNull(
            sourceIdentityKind, "Payment source mapping identity kind cannot be null");
    this.sourceIdentityValue =
        Objects.requireNonNull(
            sourceIdentityValue, "Payment source mapping identity value cannot be null");
    this.paymentId = Objects.requireNonNull(paymentId, "Mapped payment id cannot be null");
    this.sourceVersion =
        Objects.requireNonNull(sourceVersion, "Payment source mapping version cannot be null");
    this.sourceModifiedAt = sourceModifiedAt;
    this.payloadFingerprint =
        Objects.requireNonNull(
            payloadFingerprint, "Payment source mapping fingerprint cannot be null");
    this.createdAt = Objects.requireNonNull(createdAt, "Creation timestamp cannot be null");
    this.updatedAt = Objects.requireNonNull(updatedAt, "Update timestamp cannot be null");
  }

  void updateEvidence(
      String sourceVersion,
      Instant sourceModifiedAt,
      String payloadFingerprint,
      Instant updatedAt) {
    this.sourceVersion =
        Objects.requireNonNull(sourceVersion, "Payment source mapping version cannot be null");
    this.sourceModifiedAt = sourceModifiedAt;
    this.payloadFingerprint =
        Objects.requireNonNull(
            payloadFingerprint, "Payment source mapping fingerprint cannot be null");
    this.updatedAt = Objects.requireNonNull(updatedAt, "Update timestamp cannot be null");
  }

  UUID getTenantId() {
    return tenantId;
  }

  UUID getSourceSystemId() {
    return sourceSystemId;
  }

  SourceRecordIdentity.Kind getSourceIdentityKind() {
    return sourceIdentityKind;
  }

  String getSourceIdentityValue() {
    return sourceIdentityValue;
  }

  UUID getPaymentId() {
    return paymentId;
  }

  String getSourceVersion() {
    return sourceVersion;
  }

  Instant getSourceModifiedAt() {
    return sourceModifiedAt;
  }

  String getPayloadFingerprint() {
    return payloadFingerprint;
  }

  Long getVersion() {
    return version;
  }

  Instant getCreatedAt() {
    return createdAt;
  }

  Instant getUpdatedAt() {
    return updatedAt;
  }
}
