package io.github.khaledshawki.eoc.operations.application.port.in;

import io.github.khaledshawki.eoc.operations.application.model.reconciliation.ReceivableReconciliationIssue;
import io.github.khaledshawki.eoc.operations.application.model.reconciliation.ReceivableReconciliationStatus;
import io.github.khaledshawki.eoc.operations.domain.model.BusinessPartnerId;
import io.github.khaledshawki.eoc.operations.domain.model.InvoiceId;
import io.github.khaledshawki.eoc.operations.domain.model.InvoiceNumber;
import io.github.khaledshawki.eoc.operations.domain.model.InvoiceStatus;
import io.github.khaledshawki.eoc.operations.domain.model.Money;
import io.github.khaledshawki.eoc.operations.domain.model.OperationsTenantId;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Deterministic comparison of source-authoritative Invoice paid evidence and EOC-local ACTIVE
 * receivable allocations.
 *
 * <p>{@code difference = sourcePaidAmount - localAllocatedAmount}. Positive means the source is
 * ahead, negative means local allocation evidence is ahead. A structural {@code CONFLICT}
 * suppresses the signed difference because the two authorities are not safely comparable.
 */
public record ReceivableReconciliationResult(
    InvoiceId invoiceId,
    OperationsTenantId tenantId,
    BusinessPartnerId customerId,
    InvoiceNumber invoiceNumber,
    Money originalAmount,
    Money sourcePaidAmount,
    Optional<Money> localAllocatedAmount,
    Optional<Money> difference,
    InvoiceStatus sourceStatus,
    boolean cancelled,
    long activeAllocationCount,
    ReceivableReconciliationStatus status,
    Set<ReceivableReconciliationIssue> issues) {

  public ReceivableReconciliationResult {
    Objects.requireNonNull(invoiceId, "Receivable reconciliation Invoice id cannot be null");
    Objects.requireNonNull(tenantId, "Receivable reconciliation tenant id cannot be null");
    Objects.requireNonNull(customerId, "Receivable reconciliation customer id cannot be null");
    Objects.requireNonNull(
        invoiceNumber, "Receivable reconciliation Invoice number cannot be null");
    Objects.requireNonNull(
        originalAmount, "Receivable reconciliation original amount cannot be null");
    Objects.requireNonNull(
        sourcePaidAmount, "Receivable reconciliation source paid amount cannot be null");
    Objects.requireNonNull(
        localAllocatedAmount, "Receivable reconciliation local amount optional cannot be null");
    Objects.requireNonNull(
        difference, "Receivable reconciliation difference optional cannot be null");
    Objects.requireNonNull(sourceStatus, "Receivable reconciliation source status cannot be null");
    Objects.requireNonNull(status, "Receivable reconciliation status cannot be null");
    Objects.requireNonNull(issues, "Receivable reconciliation issues cannot be null");
    if (issues.stream().anyMatch(Objects::isNull)) {
      throw new NullPointerException("Receivable reconciliation issues cannot contain null");
    }
    issues = Set.copyOf(issues);

    if (!originalAmount.currency().equals(sourcePaidAmount.currency())) {
      throw new IllegalArgumentException(
          "Receivable reconciliation source amounts must use the same currency");
    }
    if (originalAmount.isNegative()
        || sourcePaidAmount.isNegative()
        || sourcePaidAmount.compareTo(originalAmount) > 0) {
      throw new IllegalArgumentException(
          "Receivable reconciliation source amounts violate Invoice monetary invariants");
    }
    if (activeAllocationCount < 0) {
      throw new IllegalArgumentException(
          "Receivable reconciliation active allocation count cannot be negative");
    }

    localAllocatedAmount.ifPresent(
        local -> {
          if (!local.currency().equals(originalAmount.currency())) {
            throw new IllegalArgumentException(
                "Receivable reconciliation local amount must use the Invoice currency");
          }
          if (local.isNegative()) {
            throw new IllegalArgumentException(
                "Receivable reconciliation local amount cannot be negative");
          }
        });
    difference.ifPresent(
        value -> {
          if (!value.currency().equals(originalAmount.currency())) {
            throw new IllegalArgumentException(
                "Receivable reconciliation difference must use the Invoice currency");
          }
        });
    if (activeAllocationCount == 0
        && localAllocatedAmount.isPresent()
        && !localAllocatedAmount.orElseThrow().isZero()) {
      throw new IllegalArgumentException(
          "No ACTIVE allocations must reconcile to zero local allocated amount");
    }
    if (activeAllocationCount > 0
        && localAllocatedAmount.isPresent()
        && !localAllocatedAmount.orElseThrow().isPositive()) {
      throw new IllegalArgumentException(
          "Comparable ACTIVE allocations must reconcile to positive local allocated amount");
    }

    InvoiceStatus expectedSourceStatus =
        expectedSourceStatus(originalAmount, sourcePaidAmount, cancelled);
    if (sourceStatus != expectedSourceStatus) {
      throw new IllegalArgumentException(
          "Receivable reconciliation source status must match canonical Invoice facts");
    }

    if (status == ReceivableReconciliationStatus.CONFLICT) {
      if (issues.isEmpty()) {
        throw new IllegalArgumentException(
            "Receivable reconciliation CONFLICT must contain at least one issue");
      }
      if (difference.isPresent()) {
        throw new IllegalArgumentException(
            "Receivable reconciliation CONFLICT cannot expose a signed difference");
      }
      if (localAllocatedAmount.isEmpty()
          && !issues.contains(ReceivableReconciliationIssue.ALLOCATION_CURRENCY_MISMATCH)) {
        throw new IllegalArgumentException(
            "Missing local amount requires an allocation currency mismatch issue");
      }
    } else {
      if (!issues.isEmpty()) {
        throw new IllegalArgumentException(
            "Non-conflicting receivable reconciliation cannot contain issues");
      }
      Money local =
          localAllocatedAmount.orElseThrow(
              () ->
                  new IllegalArgumentException(
                      "Comparable receivable reconciliation must contain local amount"));
      Money expectedDifference = sourcePaidAmount.subtract(local);
      if (!difference
          .orElseThrow(
              () ->
                  new IllegalArgumentException(
                      "Comparable receivable reconciliation must contain a difference"))
          .equals(expectedDifference)) {
        throw new IllegalArgumentException(
            "Receivable reconciliation difference must equal source paid minus local allocated");
      }
      ReceivableReconciliationStatus expectedStatus =
          expectedDifference.isZero()
              ? ReceivableReconciliationStatus.MATCHED
              : expectedDifference.isPositive()
                  ? ReceivableReconciliationStatus.SOURCE_AHEAD
                  : ReceivableReconciliationStatus.LOCAL_AHEAD;
      if (status != expectedStatus) {
        throw new IllegalArgumentException(
            "Receivable reconciliation status must match the signed difference");
      }
      if (local.compareTo(originalAmount) > 0) {
        throw new IllegalArgumentException(
            "Comparable local allocation amount cannot exceed the Invoice original amount");
      }
    }
  }

  private static InvoiceStatus expectedSourceStatus(
      Money originalAmount, Money sourcePaidAmount, boolean cancelled) {
    if (cancelled) {
      return InvoiceStatus.CANCELLED;
    }
    if (sourcePaidAmount.compareTo(originalAmount) == 0) {
      return InvoiceStatus.PAID;
    }
    if (sourcePaidAmount.isPositive()) {
      return InvoiceStatus.PARTIALLY_PAID;
    }
    return InvoiceStatus.OPEN;
  }
}
