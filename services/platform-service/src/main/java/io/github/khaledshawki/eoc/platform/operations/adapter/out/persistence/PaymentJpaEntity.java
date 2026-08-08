package io.github.khaledshawki.eoc.platform.operations.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(
    name = "operations_payments",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_operations_payments_id_tenant",
            columnNames = {"id", "tenant_id"}))
class PaymentJpaEntity {

  static final int CURRENCY_CODE_LENGTH = 3;
  static final int AMOUNT_PRECISION = 38;
  static final int AMOUNT_SCALE = 9;

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @Column(name = "tenant_id", nullable = false, updatable = false)
  private UUID tenantId;

  @Column(name = "customer_id", nullable = false)
  private UUID customerId;

  @Column(name = "currency_code", nullable = false, length = CURRENCY_CODE_LENGTH)
  private String currencyCode;

  @Column(name = "amount", nullable = false, precision = AMOUNT_PRECISION, scale = AMOUNT_SCALE)
  private BigDecimal amount;

  @Column(name = "payment_date", nullable = false)
  private LocalDate paymentDate;

  @Column(name = "reversed", nullable = false)
  private boolean reversed;

  @Version
  @Column(name = "version", nullable = false)
  private Long version;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected PaymentJpaEntity() {}

  PaymentJpaEntity(
      UUID id,
      UUID tenantId,
      UUID customerId,
      String currencyCode,
      BigDecimal amount,
      LocalDate paymentDate,
      boolean reversed,
      Instant createdAt,
      Instant updatedAt) {
    this.id = Objects.requireNonNull(id, "Payment id cannot be null");
    this.tenantId = Objects.requireNonNull(tenantId, "Payment tenant id cannot be null");
    this.customerId = Objects.requireNonNull(customerId, "Payment customer id cannot be null");
    this.currencyCode = Objects.requireNonNull(currencyCode, "Payment currency cannot be null");
    this.amount = Objects.requireNonNull(amount, "Payment amount cannot be null");
    this.paymentDate = Objects.requireNonNull(paymentDate, "Payment date cannot be null");
    this.reversed = reversed;
    this.createdAt = Objects.requireNonNull(createdAt, "Creation timestamp cannot be null");
    this.updatedAt = Objects.requireNonNull(updatedAt, "Update timestamp cannot be null");
  }

  void updateMutableState(
      UUID customerId,
      String currencyCode,
      BigDecimal amount,
      LocalDate paymentDate,
      boolean reversed,
      Instant updatedAt) {
    this.customerId = Objects.requireNonNull(customerId, "Payment customer id cannot be null");
    this.currencyCode = Objects.requireNonNull(currencyCode, "Payment currency cannot be null");
    this.amount = Objects.requireNonNull(amount, "Payment amount cannot be null");
    this.paymentDate = Objects.requireNonNull(paymentDate, "Payment date cannot be null");
    this.reversed = reversed;
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

  String getCurrencyCode() {
    return currencyCode;
  }

  BigDecimal getAmount() {
    return amount;
  }

  LocalDate getPaymentDate() {
    return paymentDate;
  }

  boolean isReversed() {
    return reversed;
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
