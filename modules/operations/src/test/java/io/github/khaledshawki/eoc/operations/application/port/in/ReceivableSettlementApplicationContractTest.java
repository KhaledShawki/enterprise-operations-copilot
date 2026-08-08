package io.github.khaledshawki.eoc.operations.application.port.in;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.khaledshawki.eoc.operations.application.model.authorization.OperationsActor;
import io.github.khaledshawki.eoc.operations.application.model.settlement.ReceivableAllocationResult;
import io.github.khaledshawki.eoc.operations.domain.model.BusinessPartnerId;
import io.github.khaledshawki.eoc.operations.domain.model.CurrencyCode;
import io.github.khaledshawki.eoc.operations.domain.model.Invoice;
import io.github.khaledshawki.eoc.operations.domain.model.InvoiceId;
import io.github.khaledshawki.eoc.operations.domain.model.InvoiceNumber;
import io.github.khaledshawki.eoc.operations.domain.model.Money;
import io.github.khaledshawki.eoc.operations.domain.model.OperationsTenantId;
import io.github.khaledshawki.eoc.operations.domain.model.Payment;
import io.github.khaledshawki.eoc.operations.domain.model.PaymentId;
import io.github.khaledshawki.eoc.operations.domain.model.ReceivableAllocation;
import io.github.khaledshawki.eoc.operations.domain.model.ReceivableAllocationId;
import io.github.khaledshawki.eoc.operations.domain.model.ReceivableAllocationState;
import io.github.khaledshawki.eoc.operations.domain.model.ReceivableSettlement;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ReceivableSettlementApplicationContractTest {

  private static final OperationsActor ACTOR = new OperationsActor("issuer", "subject");
  private static final UUID TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
  private static final UUID PAYMENT_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
  private static final UUID INVOICE_ID = UUID.fromString("00000000-0000-0000-0000-000000000003");
  private static final UUID ALLOCATION_ID = UUID.fromString("00000000-0000-0000-0000-000000000004");
  private static final CurrencyCode EUR = CurrencyCode.of("EUR");

  @Test
  void shouldRejectMissingAllocateCommandFields() {
    Money amount = Money.of("10.00", EUR);

    assertThrows(
        NullPointerException.class,
        () ->
            new AllocateReceivablePaymentCommand(
                null, TENANT_ID, PAYMENT_ID, INVOICE_ID, ALLOCATION_ID, amount));
    assertThrows(
        NullPointerException.class,
        () ->
            new AllocateReceivablePaymentCommand(
                ACTOR, null, PAYMENT_ID, INVOICE_ID, ALLOCATION_ID, amount));
    assertThrows(
        NullPointerException.class,
        () ->
            new AllocateReceivablePaymentCommand(
                ACTOR, TENANT_ID, null, INVOICE_ID, ALLOCATION_ID, amount));
    assertThrows(
        NullPointerException.class,
        () ->
            new AllocateReceivablePaymentCommand(
                ACTOR, TENANT_ID, PAYMENT_ID, null, ALLOCATION_ID, amount));
    assertThrows(
        NullPointerException.class,
        () ->
            new AllocateReceivablePaymentCommand(
                ACTOR, TENANT_ID, PAYMENT_ID, INVOICE_ID, null, amount));
    assertThrows(
        NullPointerException.class,
        () ->
            new AllocateReceivablePaymentCommand(
                ACTOR, TENANT_ID, PAYMENT_ID, INVOICE_ID, ALLOCATION_ID, null));
  }

  @Test
  void shouldRejectMissingReverseCommandFields() {
    assertThrows(
        NullPointerException.class,
        () ->
            new ReverseReceivableAllocationCommand(
                null, TENANT_ID, PAYMENT_ID, INVOICE_ID, ALLOCATION_ID));
    assertThrows(
        NullPointerException.class,
        () ->
            new ReverseReceivableAllocationCommand(
                ACTOR, null, PAYMENT_ID, INVOICE_ID, ALLOCATION_ID));
    assertThrows(
        NullPointerException.class,
        () ->
            new ReverseReceivableAllocationCommand(
                ACTOR, TENANT_ID, null, INVOICE_ID, ALLOCATION_ID));
    assertThrows(
        NullPointerException.class,
        () ->
            new ReverseReceivableAllocationCommand(
                ACTOR, TENANT_ID, PAYMENT_ID, null, ALLOCATION_ID));
    assertThrows(
        NullPointerException.class,
        () ->
            new ReverseReceivableAllocationCommand(ACTOR, TENANT_ID, PAYMENT_ID, INVOICE_ID, null));
  }

  @Test
  void shouldBuildAllocationResultOnlyFromSettlementOwnedAllocation() {
    Payment payment = payment();
    Invoice invoice = invoice();
    ReceivableSettlement settlement = ReceivableSettlement.open(payment);
    ReceivableAllocation allocation =
        settlement.allocate(
            ReceivableAllocationId.of(ALLOCATION_ID), payment, invoice, Money.of("10.00", EUR));

    ReceivableAllocationResult result = ReceivableAllocationResult.from(settlement, allocation);

    assertEquals(settlement.id(), result.settlementId());
    assertEquals(payment.id(), result.paymentId());
    assertEquals(allocation.id(), result.allocationId());
    assertEquals(invoice.id(), result.invoiceId());
    assertEquals(Money.of("10.00", EUR), result.amount());
    assertEquals(ReceivableAllocationState.ACTIVE, result.state());

    ReceivableAllocation foreign =
        ReceivableAllocation.active(
            ReceivableAllocationId.generate(), invoice.id(), Money.of("1.00", EUR));
    assertThrows(
        IllegalArgumentException.class, () -> ReceivableAllocationResult.from(settlement, foreign));
  }

  @Test
  void shouldRejectNonPositiveAllocationResultAmount() {
    Payment payment = payment();
    Invoice invoice = invoice();

    assertThrows(
        IllegalArgumentException.class,
        () ->
            new ReceivableAllocationResult(
                ReceivableSettlement.open(payment).id(),
                payment.id(),
                ReceivableAllocationId.of(ALLOCATION_ID),
                invoice.id(),
                Money.zero(EUR),
                ReceivableAllocationState.ACTIVE));
  }

  private static Payment payment() {
    return Payment.reconstitute(
        PaymentId.of(PAYMENT_ID),
        OperationsTenantId.of(TENANT_ID),
        BusinessPartnerId.of(UUID.fromString("00000000-0000-0000-0000-000000000010")),
        Money.of("100.00", EUR),
        LocalDate.of(2026, 8, 8),
        false);
  }

  private static Invoice invoice() {
    return Invoice.reconstitute(
        InvoiceId.of(INVOICE_ID),
        OperationsTenantId.of(TENANT_ID),
        BusinessPartnerId.of(UUID.fromString("00000000-0000-0000-0000-000000000010")),
        new InvoiceNumber("INV-1"),
        Money.of("100.00", EUR),
        Money.zero(EUR),
        LocalDate.of(2026, 8, 1),
        LocalDate.of(2026, 8, 31),
        false);
  }
}
