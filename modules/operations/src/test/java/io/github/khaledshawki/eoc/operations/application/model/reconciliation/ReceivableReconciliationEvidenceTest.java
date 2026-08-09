package io.github.khaledshawki.eoc.operations.application.model.reconciliation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.khaledshawki.eoc.operations.domain.model.CurrencyCode;
import io.github.khaledshawki.eoc.operations.domain.model.Money;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ReceivableReconciliationEvidenceTest {

  private static final CurrencyCode EUR = CurrencyCode.of("EUR");

  @Test
  void shouldRepresentNoActiveAllocationsAsCanonicalZero() {
    ReceivableReconciliationEvidence evidence =
        new ReceivableReconciliationEvidence(Optional.of(Money.zero(EUR)), 0, Set.of());

    assertEquals(Money.zero(EUR), evidence.localAllocatedAmount().orElseThrow());
  }

  @Test
  void shouldRepresentComparableActiveAllocationsAsPositiveMoney() {
    ReceivableReconciliationEvidence evidence =
        new ReceivableReconciliationEvidence(Optional.of(Money.of("10.00", EUR)), 1, Set.of());

    assertEquals(1, evidence.activeAllocationCount());
  }

  @Test
  void shouldRequirePositiveComparableAmountWhenActiveAllocationsExist() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new ReceivableReconciliationEvidence(Optional.of(Money.zero(EUR)), 1, Set.of()));
  }

  @Test
  void shouldRequireMissingLocalAmountForAllocationCurrencyMismatch() {
    ReceivableReconciliationEvidence evidence =
        new ReceivableReconciliationEvidence(
            Optional.empty(),
            1,
            Set.of(ReceivableReconciliationIssue.ALLOCATION_CURRENCY_MISMATCH));

    assertEquals(Optional.empty(), evidence.localAllocatedAmount());
  }

  @Test
  void shouldRejectMissingLocalAmountWithoutAllocationCurrencyMismatch() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new ReceivableReconciliationEvidence(
                Optional.empty(),
                1,
                Set.of(ReceivableReconciliationIssue.PAYMENT_REVERSED_WITH_ACTIVE_ALLOCATIONS)));
  }

  @Test
  void shouldRejectStructuralIssuesWhenNoActiveAllocationsExist() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new ReceivableReconciliationEvidence(
                Optional.of(Money.zero(EUR)),
                0,
                Set.of(ReceivableReconciliationIssue.PAYMENT_REVERSED_WITH_ACTIVE_ALLOCATIONS)));
  }
}
