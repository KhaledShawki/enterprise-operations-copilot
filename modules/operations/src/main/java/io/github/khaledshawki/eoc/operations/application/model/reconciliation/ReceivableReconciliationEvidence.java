package io.github.khaledshawki.eoc.operations.application.model.reconciliation;

import io.github.khaledshawki.eoc.operations.domain.model.Money;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * EOC-local settlement evidence used to reconcile one canonical Invoice.
 *
 * <p>The local amount is absent only when ACTIVE allocations use a currency that cannot be
 * meaningfully summed in the Invoice currency. Structural issues remain evidence rather than being
 * silently repaired.
 */
public record ReceivableReconciliationEvidence(
    Optional<Money> localAllocatedAmount,
    long activeAllocationCount,
    Set<ReceivableReconciliationIssue> issues) {

  public ReceivableReconciliationEvidence {
    Objects.requireNonNull(
        localAllocatedAmount, "Receivable reconciliation local amount optional cannot be null");
    if (activeAllocationCount < 0) {
      throw new IllegalArgumentException(
          "Receivable reconciliation active allocation count cannot be negative");
    }
    Objects.requireNonNull(issues, "Receivable reconciliation issues cannot be null");
    if (issues.stream().anyMatch(Objects::isNull)) {
      throw new NullPointerException("Receivable reconciliation issues cannot contain null");
    }
    issues = Set.copyOf(issues);

    boolean allocationCurrencyMismatch =
        issues.contains(ReceivableReconciliationIssue.ALLOCATION_CURRENCY_MISMATCH);
    if (allocationCurrencyMismatch && localAllocatedAmount.isPresent()) {
      throw new IllegalArgumentException(
          "Local allocated amount cannot be represented when allocation currencies mismatch");
    }
    if (!allocationCurrencyMismatch && localAllocatedAmount.isEmpty()) {
      throw new IllegalArgumentException(
          "Local allocated amount must be present when allocation currencies are comparable");
    }
    localAllocatedAmount.ifPresent(
        amount -> {
          if (amount.isNegative()) {
            throw new IllegalArgumentException(
                "Receivable reconciliation local allocated amount cannot be negative");
          }
        });

    if (activeAllocationCount == 0) {
      if (localAllocatedAmount.isEmpty() || !localAllocatedAmount.orElseThrow().isZero()) {
        throw new IllegalArgumentException(
            "No ACTIVE allocations must reconcile to a represented zero local amount");
      }
      if (!issues.isEmpty()) {
        throw new IllegalArgumentException(
            "No ACTIVE allocations cannot carry local settlement structural issues");
      }
    } else if (localAllocatedAmount.isPresent()
        && !localAllocatedAmount.orElseThrow().isPositive()) {
      throw new IllegalArgumentException(
          "Comparable ACTIVE allocations must reconcile to a positive local amount");
    }
  }
}
