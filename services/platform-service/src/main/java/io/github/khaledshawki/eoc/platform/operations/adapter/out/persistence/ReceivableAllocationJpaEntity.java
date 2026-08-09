package io.github.khaledshawki.eoc.platform.operations.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@IdClass(ReceivableAllocationJpaId.class)
@Table(
    name = "operations_receivable_allocations",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_operations_receivable_allocations_settlement_position",
            columnNames = {"tenant_id", "settlement_id", "allocation_position"}))
class ReceivableAllocationJpaEntity {

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @Id
  @Column(name = "tenant_id", nullable = false, updatable = false)
  private UUID tenantId;

  @Column(name = "settlement_id", nullable = false, updatable = false)
  private UUID settlementId;

  @Column(name = "invoice_id", nullable = false, updatable = false)
  private UUID invoiceId;

  @Column(name = "currency_code", nullable = false, updatable = false, length = 3)
  private String currencyCode;

  @Column(name = "amount", nullable = false, updatable = false, precision = 38, scale = 9)
  private BigDecimal amount;

  @Column(name = "state", nullable = false, length = 16)
  private String state;

  @Column(name = "allocation_position", nullable = false, updatable = false)
  private int allocationPosition;

  @Version
  @Column(name = "version", nullable = false)
  private Long version;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected ReceivableAllocationJpaEntity() {}

  ReceivableAllocationJpaEntity(
      UUID id,
      UUID tenantId,
      UUID settlementId,
      UUID invoiceId,
      String currencyCode,
      BigDecimal amount,
      String state,
      int allocationPosition,
      Instant createdAt,
      Instant updatedAt) {
    this.id = Objects.requireNonNull(id, "Receivable allocation id cannot be null");
    this.tenantId =
        Objects.requireNonNull(tenantId, "Receivable allocation tenant id cannot be null");
    this.settlementId =
        Objects.requireNonNull(settlementId, "Receivable allocation settlement id cannot be null");
    this.invoiceId =
        Objects.requireNonNull(invoiceId, "Receivable allocation invoice id cannot be null");
    this.currencyCode =
        Objects.requireNonNull(currencyCode, "Receivable allocation currency cannot be null");
    this.amount = Objects.requireNonNull(amount, "Receivable allocation amount cannot be null");
    this.state = Objects.requireNonNull(state, "Receivable allocation state cannot be null");
    if (allocationPosition < 0) {
      throw new IllegalArgumentException("Receivable allocation position cannot be negative");
    }
    this.allocationPosition = allocationPosition;
    this.createdAt = Objects.requireNonNull(createdAt, "Creation timestamp cannot be null");
    this.updatedAt = Objects.requireNonNull(updatedAt, "Update timestamp cannot be null");
  }

  void updateState(String state, Instant updatedAt) {
    this.state = Objects.requireNonNull(state, "Receivable allocation state cannot be null");
    this.updatedAt = Objects.requireNonNull(updatedAt, "Update timestamp cannot be null");
  }

  UUID getId() {
    return id;
  }

  UUID getTenantId() {
    return tenantId;
  }

  UUID getSettlementId() {
    return settlementId;
  }

  UUID getInvoiceId() {
    return invoiceId;
  }

  String getCurrencyCode() {
    return currencyCode;
  }

  BigDecimal getAmount() {
    return amount;
  }

  String getState() {
    return state;
  }

  int getAllocationPosition() {
    return allocationPosition;
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
