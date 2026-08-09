package io.github.khaledshawki.eoc.platform.operations.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(
    name = "operations_receivable_settlements",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uk_operations_receivable_settlements_id_tenant",
          columnNames = {"id", "tenant_id"}),
      @UniqueConstraint(
          name = "uk_operations_receivable_settlements_tenant_payment",
          columnNames = {"tenant_id", "payment_id"})
    })
class ReceivableSettlementJpaEntity {

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @Column(name = "tenant_id", nullable = false, updatable = false)
  private UUID tenantId;

  @Column(name = "customer_id", nullable = false, updatable = false)
  private UUID customerId;

  @Column(name = "payment_id", nullable = false, updatable = false)
  private UUID paymentId;

  @Column(name = "currency_code", nullable = false, updatable = false, length = 3)
  private String currencyCode;

  @Version
  @Column(name = "version", nullable = false)
  private Long version;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected ReceivableSettlementJpaEntity() {}

  ReceivableSettlementJpaEntity(
      UUID id,
      UUID tenantId,
      UUID customerId,
      UUID paymentId,
      String currencyCode,
      Instant createdAt,
      Instant updatedAt) {
    this.id = Objects.requireNonNull(id, "Receivable settlement id cannot be null");
    this.tenantId =
        Objects.requireNonNull(tenantId, "Receivable settlement tenant id cannot be null");
    this.customerId =
        Objects.requireNonNull(customerId, "Receivable settlement customer id cannot be null");
    this.paymentId =
        Objects.requireNonNull(paymentId, "Receivable settlement payment id cannot be null");
    this.currencyCode =
        Objects.requireNonNull(currencyCode, "Receivable settlement currency cannot be null");
    this.createdAt = Objects.requireNonNull(createdAt, "Creation timestamp cannot be null");
    this.updatedAt = Objects.requireNonNull(updatedAt, "Update timestamp cannot be null");
  }

  void touch(Instant updatedAt) {
    this.updatedAt = Objects.requireNonNull(updatedAt, "Update timestamp cannot be null");
  }

  UUID getId() {
    return id;
  }

  UUID getTenantId() {
    return tenantId;
  }

  UUID getCustomerId() {
    return customerId;
  }

  UUID getPaymentId() {
    return paymentId;
  }

  String getCurrencyCode() {
    return currencyCode;
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
