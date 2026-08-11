package io.github.khaledshawki.eoc.analytics.domain.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class InvoiceReceivableProjectionTest {

  @Test
  void derivesOutstandingAmountAndOverdueState() {
    InvoiceReceivableProjection projection =
        projection(
            "100.00",
            "25.00",
            false,
            InvoiceReceivableStatus.PARTIALLY_PAID,
            LocalDate.of(2026, 7, 31));

    assertEquals(new BigDecimal("75.00"), projection.outstandingAmount().amount());
    assertTrue(projection.isOverdueOn(LocalDate.of(2026, 8, 12)));
    assertFalse(projection.isOverdueOn(LocalDate.of(2026, 7, 31)));
  }

  @Test
  void paidInvoiceIsNotOverdue() {
    InvoiceReceivableProjection projection =
        projection(
            "100.00", "100.00", false, InvoiceReceivableStatus.PAID, LocalDate.of(2026, 7, 31));

    assertFalse(projection.isOverdueOn(LocalDate.of(2026, 8, 12)));
  }

  @Test
  void cancelledInvoiceIsNotOverdueEvenWithOutstandingCanonicalAmount() {
    InvoiceReceivableProjection projection =
        projection(
            "100.00", "0.00", true, InvoiceReceivableStatus.CANCELLED, LocalDate.of(2026, 7, 31));

    assertEquals(new BigDecimal("100.00"), projection.outstandingAmount().amount());
    assertFalse(projection.isOverdueOn(LocalDate.of(2026, 8, 12)));
  }

  @Test
  void rejectsStatusThatDoesNotMatchFacts() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            projection(
                "100.00", "25.00", false, InvoiceReceivableStatus.OPEN, LocalDate.of(2026, 7, 31)));
  }

  @Test
  void rejectsPaidAmountAboveOriginalAmount() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            projection(
                "100.00",
                "101.00",
                false,
                InvoiceReceivableStatus.PAID,
                LocalDate.of(2026, 7, 31)));
  }

  @Test
  void rejectsDueDateBeforeIssueDate() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new InvoiceReceivableProjection(
                AnalyticsTenantId.of(UUID.randomUUID()),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "INV-1",
                AnalyticsMoney.of(new BigDecimal("100.00"), "EUR"),
                AnalyticsMoney.of(new BigDecimal("0.00"), "EUR"),
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 7, 31),
                false,
                InvoiceReceivableStatus.OPEN,
                new ProjectionCursor(UUID.randomUUID(), 1, Instant.parse("2026-08-12T00:00:00Z"))));
  }

  private static InvoiceReceivableProjection projection(
      String original,
      String paid,
      boolean cancelled,
      InvoiceReceivableStatus status,
      LocalDate dueDate) {
    return new InvoiceReceivableProjection(
        AnalyticsTenantId.of(UUID.randomUUID()),
        UUID.randomUUID(),
        UUID.randomUUID(),
        " INV-1 ",
        AnalyticsMoney.of(new BigDecimal(original), "EUR"),
        AnalyticsMoney.of(new BigDecimal(paid), "EUR"),
        LocalDate.of(2026, 7, 1),
        dueDate,
        cancelled,
        status,
        new ProjectionCursor(UUID.randomUUID(), 1, Instant.parse("2026-08-12T00:00:00Z")));
  }
}
