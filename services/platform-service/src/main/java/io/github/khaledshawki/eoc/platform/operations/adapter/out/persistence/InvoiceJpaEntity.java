package io.github.khaledshawki.eoc.platform.operations.adapter.out.persistence;

import io.github.khaledshawki.eoc.operations.domain.model.InvoiceNumber;
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
    name = "operations_invoices",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_operations_invoices_id_tenant",
            columnNames = {"id", "tenant_id"}))
class InvoiceJpaEntity {

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

  @Column(name = "invoice_number", nullable = false, length = InvoiceNumber.MAX_LENGTH)
  private String invoiceNumber;

  @Column(name = "currency_code", nullable = false, length = CURRENCY_CODE_LENGTH)
  private String currencyCode;

  @Column(
      name = "original_amount",
      nullable = false,
      precision = AMOUNT_PRECISION,
      scale = AMOUNT_SCALE)
  private BigDecimal originalAmount;

  @Column(
      name = "paid_amount",
      nullable = false,
      precision = AMOUNT_PRECISION,
      scale = AMOUNT_SCALE)
  private BigDecimal paidAmount;

  @Column(name = "issue_date", nullable = false)
  private LocalDate issueDate;

  @Column(name = "due_date", nullable = false)
  private LocalDate dueDate;

  @Column(name = "cancelled", nullable = false)
  private boolean cancelled;

  @Version
  @Column(name = "version", nullable = false)
  private Long version;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected InvoiceJpaEntity() {}

  InvoiceJpaEntity(
      UUID id,
      UUID tenantId,
      UUID customerId,
      String invoiceNumber,
      String currencyCode,
      BigDecimal originalAmount,
      BigDecimal paidAmount,
      LocalDate issueDate,
      LocalDate dueDate,
      boolean cancelled,
      Instant createdAt,
      Instant updatedAt) {
    this.id = Objects.requireNonNull(id, "Invoice id cannot be null");
    this.tenantId = Objects.requireNonNull(tenantId, "Invoice tenant id cannot be null");
    this.customerId = Objects.requireNonNull(customerId, "Invoice customer id cannot be null");
    this.invoiceNumber = Objects.requireNonNull(invoiceNumber, "Invoice number cannot be null");
    this.currencyCode = Objects.requireNonNull(currencyCode, "Invoice currency cannot be null");
    this.originalAmount =
        Objects.requireNonNull(originalAmount, "Invoice original amount cannot be null");
    this.paidAmount = Objects.requireNonNull(paidAmount, "Invoice paid amount cannot be null");
    this.issueDate = Objects.requireNonNull(issueDate, "Invoice issue date cannot be null");
    this.dueDate = Objects.requireNonNull(dueDate, "Invoice due date cannot be null");
    this.cancelled = cancelled;
    this.createdAt = Objects.requireNonNull(createdAt, "Creation timestamp cannot be null");
    this.updatedAt = Objects.requireNonNull(updatedAt, "Update timestamp cannot be null");
  }

  void updateMutableState(
      UUID customerId,
      String invoiceNumber,
      String currencyCode,
      BigDecimal originalAmount,
      BigDecimal paidAmount,
      LocalDate issueDate,
      LocalDate dueDate,
      boolean cancelled,
      Instant updatedAt) {
    this.customerId = Objects.requireNonNull(customerId, "Invoice customer id cannot be null");
    this.invoiceNumber = Objects.requireNonNull(invoiceNumber, "Invoice number cannot be null");
    this.currencyCode = Objects.requireNonNull(currencyCode, "Invoice currency cannot be null");
    this.originalAmount =
        Objects.requireNonNull(originalAmount, "Invoice original amount cannot be null");
    this.paidAmount = Objects.requireNonNull(paidAmount, "Invoice paid amount cannot be null");
    this.issueDate = Objects.requireNonNull(issueDate, "Invoice issue date cannot be null");
    this.dueDate = Objects.requireNonNull(dueDate, "Invoice due date cannot be null");
    this.cancelled = cancelled;
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

  String getInvoiceNumber() {
    return invoiceNumber;
  }

  String getCurrencyCode() {
    return currencyCode;
  }

  BigDecimal getOriginalAmount() {
    return originalAmount;
  }

  BigDecimal getPaidAmount() {
    return paidAmount;
  }

  LocalDate getIssueDate() {
    return issueDate;
  }

  LocalDate getDueDate() {
    return dueDate;
  }

  boolean isCancelled() {
    return cancelled;
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
