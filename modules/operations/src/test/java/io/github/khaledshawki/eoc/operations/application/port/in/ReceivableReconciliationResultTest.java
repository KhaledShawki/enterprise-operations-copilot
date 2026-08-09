package io.github.khaledshawki.eoc.operations.application.port.in;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.khaledshawki.eoc.operations.application.model.reconciliation.ReceivableReconciliationIssue;
import io.github.khaledshawki.eoc.operations.application.model.reconciliation.ReceivableReconciliationStatus;
import io.github.khaledshawki.eoc.operations.domain.model.BusinessPartnerId;
import io.github.khaledshawki.eoc.operations.domain.model.CurrencyCode;
import io.github.khaledshawki.eoc.operations.domain.model.InvoiceId;
import io.github.khaledshawki.eoc.operations.domain.model.InvoiceNumber;
import io.github.khaledshawki.eoc.operations.domain.model.InvoiceStatus;
import io.github.khaledshawki.eoc.operations.domain.model.Money;
import io.github.khaledshawki.eoc.operations.domain.model.OperationsTenantId;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ReceivableReconciliationResultTest {

  private static final CurrencyCode EUR = CurrencyCode.of("EUR");
  private static final InvoiceId INVOICE_ID =
      InvoiceId.of(UUID.fromString("00000000-0000-0000-0000-000000000001"));
  private static final OperationsTenantId TENANT_ID =
      OperationsTenantId.of(UUID.fromString("00000000-0000-0000-0000-000000000002"));
  private static final BusinessPartnerId CUSTOMER_ID =
      BusinessPartnerId.of(UUID.fromString("00000000-0000-0000-0000-000000000003"));

  @Test
  void shouldAcceptMatchedEvidence() {
    ReceivableReconciliationResult result =
        result(
            "100.00",
            "40.00",
            Optional.of("40.00"),
            Optional.of("0.00"),
            ReceivableReconciliationStatus.MATCHED,
            Set.of(),
            false,
            2);

    assertEquals(ReceivableReconciliationStatus.MATCHED, result.status());
  }

  @Test
  void shouldAcceptSourceAheadEvidenceWithPositiveDifference() {
    ReceivableReconciliationResult result =
        result(
            "100.00",
            "60.00",
            Optional.of("20.00"),
            Optional.of("40.00"),
            ReceivableReconciliationStatus.SOURCE_AHEAD,
            Set.of(),
            false,
            1);

    assertEquals(Money.of("40.00", EUR), result.difference().orElseThrow());
  }

  @Test
  void shouldAcceptLocalAheadEvidenceWithNegativeDifference() {
    ReceivableReconciliationResult result =
        result(
            "100.00",
            "10.00",
            Optional.of("30.00"),
            Optional.of("-20.00"),
            ReceivableReconciliationStatus.LOCAL_AHEAD,
            Set.of(),
            false,
            1);

    assertEquals(Money.of("-20.00", EUR), result.difference().orElseThrow());
  }

  @Test
  void shouldAcceptConflictAndSuppressDifference() {
    ReceivableReconciliationResult result =
        result(
            "100.00",
            "20.00",
            Optional.of("20.00"),
            Optional.empty(),
            ReceivableReconciliationStatus.CONFLICT,
            Set.of(ReceivableReconciliationIssue.PAYMENT_REVERSED_WITH_ACTIVE_ALLOCATIONS),
            false,
            1);

    assertEquals(Optional.empty(), result.difference());
  }

  @Test
  void shouldAllowMissingLocalAmountOnlyForAllocationCurrencyConflict() {
    ReceivableReconciliationResult result =
        result(
            "100.00",
            "20.00",
            Optional.empty(),
            Optional.empty(),
            ReceivableReconciliationStatus.CONFLICT,
            Set.of(ReceivableReconciliationIssue.ALLOCATION_CURRENCY_MISMATCH),
            false,
            1);

    assertEquals(Optional.empty(), result.localAllocatedAmount());
  }

  @Test
  void shouldRejectStatusThatDoesNotMatchDifference() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            result(
                "100.00",
                "60.00",
                Optional.of("20.00"),
                Optional.of("40.00"),
                ReceivableReconciliationStatus.LOCAL_AHEAD,
                Set.of(),
                false,
                1));
  }

  @Test
  void shouldRejectConflictWithoutIssues() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            result(
                "100.00",
                "20.00",
                Optional.of("20.00"),
                Optional.empty(),
                ReceivableReconciliationStatus.CONFLICT,
                Set.of(),
                false,
                1));
  }

  @Test
  void shouldRejectNonConflictWithIssues() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            result(
                "100.00",
                "20.00",
                Optional.of("20.00"),
                Optional.of("0.00"),
                ReceivableReconciliationStatus.MATCHED,
                Set.of(ReceivableReconciliationIssue.SETTLEMENT_CUSTOMER_MISMATCH),
                false,
                1));
  }

  @Test
  void shouldRejectIncorrectSignedDifference() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            result(
                "100.00",
                "60.00",
                Optional.of("20.00"),
                Optional.of("-40.00"),
                ReceivableReconciliationStatus.SOURCE_AHEAD,
                Set.of(),
                false,
                1));
  }

  @Test
  void shouldDefensivelyCopyIssues() {
    HashSet<ReceivableReconciliationIssue> issues = new HashSet<>();
    issues.add(ReceivableReconciliationIssue.PAYMENT_REVERSED_WITH_ACTIVE_ALLOCATIONS);
    ReceivableReconciliationResult result =
        result(
            "100.00",
            "20.00",
            Optional.of("20.00"),
            Optional.empty(),
            ReceivableReconciliationStatus.CONFLICT,
            issues,
            false,
            1);

    issues.add(ReceivableReconciliationIssue.PAYMENT_CUSTOMER_MISMATCH);

    assertEquals(
        Set.of(ReceivableReconciliationIssue.PAYMENT_REVERSED_WITH_ACTIVE_ALLOCATIONS),
        result.issues());
  }

  private static ReceivableReconciliationResult result(
      String original,
      String sourcePaid,
      Optional<String> local,
      Optional<String> difference,
      ReceivableReconciliationStatus status,
      Set<ReceivableReconciliationIssue> issues,
      boolean cancelled,
      long activeAllocationCount) {
    Money originalAmount = Money.of(original, EUR);
    Money sourcePaidAmount = Money.of(sourcePaid, EUR);
    InvoiceStatus sourceStatus =
        cancelled
            ? InvoiceStatus.CANCELLED
            : sourcePaidAmount.compareTo(originalAmount) == 0
                ? InvoiceStatus.PAID
                : sourcePaidAmount.isPositive() ? InvoiceStatus.PARTIALLY_PAID : InvoiceStatus.OPEN;
    return new ReceivableReconciliationResult(
        INVOICE_ID,
        TENANT_ID,
        CUSTOMER_ID,
        new InvoiceNumber("INV-1"),
        originalAmount,
        sourcePaidAmount,
        local.map(value -> Money.of(value, EUR)),
        difference.map(value -> Money.of(value, EUR)),
        sourceStatus,
        cancelled,
        activeAllocationCount,
        status,
        issues);
  }
}
