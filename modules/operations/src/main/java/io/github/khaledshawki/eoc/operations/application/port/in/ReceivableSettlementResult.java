package io.github.khaledshawki.eoc.operations.application.port.in;

import io.github.khaledshawki.eoc.operations.application.model.settlement.ReceivableAllocationResult;
import io.github.khaledshawki.eoc.operations.domain.model.Money;
import io.github.khaledshawki.eoc.operations.domain.model.ReceivableAllocationState;
import io.github.khaledshawki.eoc.operations.domain.model.ReceivableSettlementId;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Payment-rooted local receivable settlement state.
 *
 * <p>{@code settlementId} is empty until the first local allocation is persisted. In that state the
 * Payment is fully unapplied, so {@code allocatedAmount} is zero and {@code allocations} is empty.
 */
public record ReceivableSettlementResult(
    PaymentResult payment,
    Optional<ReceivableSettlementId> settlementId,
    Money allocatedAmount,
    Money unappliedAmount,
    List<ReceivableAllocationResult> allocations) {

  public ReceivableSettlementResult {
    Objects.requireNonNull(payment, "Receivable settlement payment result cannot be null");
    Objects.requireNonNull(settlementId, "Receivable settlement id optional cannot be null");
    Objects.requireNonNull(allocatedAmount, "Receivable allocated amount cannot be null");
    Objects.requireNonNull(unappliedAmount, "Receivable unapplied amount cannot be null");
    Objects.requireNonNull(allocations, "Receivable settlement allocations cannot be null");
    if (allocations.stream().anyMatch(Objects::isNull)) {
      throw new NullPointerException("Receivable settlement allocations cannot contain null");
    }
    allocations = List.copyOf(allocations);

    if (!allocatedAmount.currency().equals(payment.amount().currency())
        || !unappliedAmount.currency().equals(payment.amount().currency())) {
      throw new IllegalArgumentException(
          "Receivable settlement summary amounts must use the Payment currency");
    }
    if (allocatedAmount.isNegative() || unappliedAmount.isNegative()) {
      throw new IllegalArgumentException(
          "Receivable settlement summary amounts cannot be negative");
    }
    if (!allocatedAmount.add(unappliedAmount).equals(payment.effectiveAmount())) {
      throw new IllegalArgumentException(
          "Receivable allocated and unapplied amounts must equal the Payment effective amount");
    }

    if (settlementId.isEmpty()) {
      if (!allocations.isEmpty() || !allocatedAmount.isZero()) {
        throw new IllegalArgumentException(
            "A Payment without a settlement cannot contain local allocations");
      }
    } else {
      Money activeTotal = Money.zero(payment.amount().currency());
      for (ReceivableAllocationResult allocation : allocations) {
        if (!allocation.paymentId().equals(payment.paymentId())) {
          throw new IllegalArgumentException(
              "Receivable allocation result belongs to another Payment");
        }
        if (!allocation.settlementId().equals(settlementId.orElseThrow())) {
          throw new IllegalArgumentException(
              "Receivable allocation result belongs to another settlement");
        }
        if (!allocation.amount().currency().equals(payment.amount().currency())) {
          throw new IllegalArgumentException("Receivable allocation result uses another currency");
        }
        if (allocation.state() == ReceivableAllocationState.ACTIVE) {
          activeTotal = activeTotal.add(allocation.amount());
        }
      }
      if (!activeTotal.equals(allocatedAmount)) {
        throw new IllegalArgumentException(
            "Receivable allocated amount must equal the ACTIVE allocation total");
      }
    }
  }
}
