package io.github.khaledshawki.eoc.operations.domain.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class InvoiceTest {

  private static final OperationsTenantId TENANT_ID =
      OperationsTenantId.of(UUID.fromString("00000000-0000-0000-0000-000000000001"));
  private static final BusinessPartnerId CUSTOMER_ID =
      BusinessPartnerId.of(UUID.fromString("00000000-0000-0000-0000-000000000101"));
  private static final BusinessPartnerId OTHER_CUSTOMER_ID =
      BusinessPartnerId.of(UUID.fromString("00000000-0000-0000-0000-000000000102"));
  private static final CurrencyCode EUR = CurrencyCode.of("EUR");
  private static final LocalDate ISSUE_DATE = LocalDate.of(2026, 8, 1);
  private static final LocalDate DUE_DATE = LocalDate.of(2026, 8, 31);

  @Test
  void shouldImportOpenCustomerInvoiceWithGeneratedIdentity() {
    Invoice invoice = invoice("100.00", "0.00", false);
    Invoice another = invoice("100.00", "0.00", false);

    assertNotEquals(invoice.id(), another.id());
    assertEquals(TENANT_ID, invoice.tenantId());
    assertEquals(CUSTOMER_ID, invoice.customerId());
    assertEquals(new InvoiceNumber("INV-100"), invoice.invoiceNumber());
    assertEquals(Money.of("100.00", EUR), invoice.originalAmount());
    assertEquals(Money.of("0.00", EUR), invoice.paidAmount());
    assertEquals(Money.of("100.00", EUR), invoice.remainingAmount());
    assertEquals(Money.of("100.00", EUR), invoice.openAmount());
    assertEquals(InvoiceStatus.OPEN, invoice.status());
  }

  @Test
  void shouldReconstituteInvoiceWithExistingIdentity() {
    InvoiceId id = InvoiceId.of(UUID.fromString("00000000-0000-0000-0000-000000000201"));

    Invoice invoice =
        Invoice.reconstitute(
            id,
            TENANT_ID,
            CUSTOMER_ID,
            new InvoiceNumber("INV-100"),
            Money.of("100.00", EUR),
            Money.of("25.00", EUR),
            ISSUE_DATE,
            DUE_DATE,
            false);

    assertEquals(id, invoice.id());
    assertEquals(InvoiceStatus.PARTIALLY_PAID, invoice.status());
  }

  @Test
  void shouldDeriveOpenPartiallyPaidPaidAndCancelledStatuses() {
    assertEquals(InvoiceStatus.OPEN, invoice("100.00", "0.00", false).status());
    assertEquals(InvoiceStatus.PARTIALLY_PAID, invoice("100.00", "25.00", false).status());
    assertEquals(InvoiceStatus.PAID, invoice("100.00", "100.00", false).status());
    assertEquals(InvoiceStatus.CANCELLED, invoice("100.00", "25.00", true).status());
  }

  @Test
  void shouldTreatZeroValueInvoiceAsPaid() {
    Invoice invoice = invoice("0.00", "0.00", false);

    assertEquals(InvoiceStatus.PAID, invoice.status());
    assertEquals(Money.zero(EUR), invoice.remainingAmount());
    assertEquals(Money.zero(EUR), invoice.openAmount());
  }

  @Test
  void shouldKeepRemainingAmountButExposeZeroOpenAmountWhenCancelled() {
    Invoice invoice = invoice("100.00", "25.00", true);

    assertEquals(Money.of("75.00", EUR), invoice.remainingAmount());
    assertEquals(Money.zero(EUR), invoice.openAmount());
  }

  @Test
  void shouldDeriveOverdueUsingExplicitBusinessDate() {
    Invoice open = invoice("100.00", "0.00", false);
    Invoice partial = invoice("100.00", "25.00", false);
    Invoice paid = invoice("100.00", "100.00", false);
    Invoice cancelled = invoice("100.00", "0.00", true);

    assertFalse(open.isOverdue(DUE_DATE));
    assertTrue(open.isOverdue(DUE_DATE.plusDays(1)));
    assertTrue(partial.isOverdue(DUE_DATE.plusDays(1)));
    assertFalse(paid.isOverdue(DUE_DATE.plusDays(1)));
    assertFalse(cancelled.isOverdue(DUE_DATE.plusDays(1)));
    assertThrows(NullPointerException.class, () -> open.isOverdue(null));
  }

  @Test
  void shouldRejectMissingInvoiceIdentityWhenReconstituting() {
    assertThrows(
        NullPointerException.class,
        () ->
            Invoice.reconstitute(
                null,
                TENANT_ID,
                CUSTOMER_ID,
                new InvoiceNumber("INV-100"),
                Money.of("100.00", EUR),
                Money.zero(EUR),
                ISSUE_DATE,
                DUE_DATE,
                false));
  }

  @Test
  void shouldRejectMissingInvoiceFacts() {
    Money oneHundredEuros = Money.of("100.00", EUR);
    Money zeroEuros = Money.zero(EUR);
    InvoiceNumber invoiceNumber = new InvoiceNumber("INV-100");

    assertThrows(
        NullPointerException.class,
        () ->
            Invoice.importCustomerInvoice(
                null,
                CUSTOMER_ID,
                invoiceNumber,
                oneHundredEuros,
                zeroEuros,
                ISSUE_DATE,
                DUE_DATE,
                false));
    assertThrows(
        NullPointerException.class,
        () ->
            Invoice.importCustomerInvoice(
                TENANT_ID,
                null,
                invoiceNumber,
                oneHundredEuros,
                zeroEuros,
                ISSUE_DATE,
                DUE_DATE,
                false));
    assertThrows(
        NullPointerException.class,
        () ->
            Invoice.importCustomerInvoice(
                TENANT_ID,
                CUSTOMER_ID,
                null,
                oneHundredEuros,
                zeroEuros,
                ISSUE_DATE,
                DUE_DATE,
                false));
    assertThrows(
        NullPointerException.class,
        () ->
            Invoice.importCustomerInvoice(
                TENANT_ID,
                CUSTOMER_ID,
                invoiceNumber,
                null,
                zeroEuros,
                ISSUE_DATE,
                DUE_DATE,
                false));
    assertThrows(
        NullPointerException.class,
        () ->
            Invoice.importCustomerInvoice(
                TENANT_ID,
                CUSTOMER_ID,
                invoiceNumber,
                oneHundredEuros,
                null,
                ISSUE_DATE,
                DUE_DATE,
                false));
    assertThrows(
        NullPointerException.class,
        () ->
            Invoice.importCustomerInvoice(
                TENANT_ID,
                CUSTOMER_ID,
                invoiceNumber,
                oneHundredEuros,
                zeroEuros,
                null,
                DUE_DATE,
                false));
    assertThrows(
        NullPointerException.class,
        () ->
            Invoice.importCustomerInvoice(
                TENANT_ID,
                CUSTOMER_ID,
                invoiceNumber,
                oneHundredEuros,
                zeroEuros,
                ISSUE_DATE,
                null,
                false));
  }

  @Test
  void shouldRejectInvalidInvoiceFacts() {
    Money oneHundredEuros = Money.of("100.00", EUR);
    Money zeroEuros = Money.zero(EUR);

    assertThrows(IllegalArgumentException.class, () -> invoice("-1.00", "0.00", false));
    assertThrows(IllegalArgumentException.class, () -> invoice("100.00", "-1.00", false));
    assertThrows(IllegalArgumentException.class, () -> invoice("100.00", "100.01", false));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            Invoice.importCustomerInvoice(
                TENANT_ID,
                CUSTOMER_ID,
                new InvoiceNumber("INV-100"),
                oneHundredEuros,
                Money.of("0.00", CurrencyCode.of("USD")),
                ISSUE_DATE,
                DUE_DATE,
                false));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            Invoice.importCustomerInvoice(
                TENANT_ID,
                CUSTOMER_ID,
                new InvoiceNumber("INV-100"),
                oneHundredEuros,
                zeroEuros,
                ISSUE_DATE,
                ISSUE_DATE.minusDays(1),
                false));
  }

  @Test
  void shouldAcceptInvoiceDueOnIssueDate() {
    Invoice invoice =
        Invoice.importCustomerInvoice(
            TENANT_ID,
            CUSTOMER_ID,
            new InvoiceNumber("INV-100"),
            Money.of("100.00", EUR),
            Money.zero(EUR),
            ISSUE_DATE,
            ISSUE_DATE,
            false);

    assertEquals(ISSUE_DATE, invoice.dueDate());
  }

  @Test
  void shouldSynchronizeCompleteAuthoritativeSnapshot() {
    Invoice invoice = invoice("100.00", "0.00", false);
    InvoiceId id = invoice.id();

    invoice.synchronizeCustomerInvoice(
        OTHER_CUSTOMER_ID,
        new InvoiceNumber("INV-101"),
        Money.of("120.00", EUR),
        Money.of("20.00", EUR),
        ISSUE_DATE.plusDays(1),
        DUE_DATE.plusDays(1),
        true);

    assertEquals(id, invoice.id());
    assertEquals(TENANT_ID, invoice.tenantId());
    assertEquals(OTHER_CUSTOMER_ID, invoice.customerId());
    assertEquals(new InvoiceNumber("INV-101"), invoice.invoiceNumber());
    assertEquals(Money.of("120.00", EUR), invoice.originalAmount());
    assertEquals(Money.of("20.00", EUR), invoice.paidAmount());
    assertEquals(ISSUE_DATE.plusDays(1), invoice.issueDate());
    assertEquals(DUE_DATE.plusDays(1), invoice.dueDate());
    assertTrue(invoice.cancelled());
    assertEquals(InvoiceStatus.CANCELLED, invoice.status());
  }

  @Test
  void shouldLeaveAggregateUnchangedWhenSynchronizationIsInvalid() {
    Invoice invoice = invoice("100.00", "25.00", false);

    assertThrows(
        IllegalArgumentException.class,
        () ->
            invoice.synchronizeCustomerInvoice(
                OTHER_CUSTOMER_ID,
                new InvoiceNumber("INV-INVALID"),
                Money.of("50.00", EUR),
                Money.of("60.00", EUR),
                ISSUE_DATE.plusDays(1),
                DUE_DATE.plusDays(1),
                true));

    assertEquals(CUSTOMER_ID, invoice.customerId());
    assertEquals(new InvoiceNumber("INV-100"), invoice.invoiceNumber());
    assertEquals(Money.of("100.00", EUR), invoice.originalAmount());
    assertEquals(Money.of("25.00", EUR), invoice.paidAmount());
    assertEquals(ISSUE_DATE, invoice.issueDate());
    assertEquals(DUE_DATE, invoice.dueDate());
    assertFalse(invoice.cancelled());
    assertEquals(InvoiceStatus.PARTIALLY_PAID, invoice.status());
  }

  @Test
  void shouldReopenCancelledInvoiceFromNewAuthoritativeSnapshot() {
    Invoice invoice = invoice("100.00", "25.00", true);

    invoice.synchronizeCustomerInvoice(
        CUSTOMER_ID,
        new InvoiceNumber("INV-100"),
        Money.of("100.00", EUR),
        Money.of("25.00", EUR),
        ISSUE_DATE,
        DUE_DATE,
        false);

    assertFalse(invoice.cancelled());
    assertEquals(InvoiceStatus.PARTIALLY_PAID, invoice.status());
    assertEquals(Money.of("75.00", EUR), invoice.openAmount());
  }

  private static Invoice invoice(String originalAmount, String paidAmount, boolean cancelled) {
    return Invoice.importCustomerInvoice(
        TENANT_ID,
        CUSTOMER_ID,
        new InvoiceNumber("INV-100"),
        Money.of(originalAmount, EUR),
        Money.of(paidAmount, EUR),
        ISSUE_DATE,
        DUE_DATE,
        cancelled);
  }
}
