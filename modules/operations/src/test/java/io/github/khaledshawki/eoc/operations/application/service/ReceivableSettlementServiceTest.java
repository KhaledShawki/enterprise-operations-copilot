package io.github.khaledshawki.eoc.operations.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.khaledshawki.eoc.operations.application.exception.InvoiceNotFoundException;
import io.github.khaledshawki.eoc.operations.application.exception.OperationsAccessDeniedException;
import io.github.khaledshawki.eoc.operations.application.exception.PaymentNotFoundException;
import io.github.khaledshawki.eoc.operations.application.exception.ReceivableAllocationNotFoundException;
import io.github.khaledshawki.eoc.operations.application.exception.ReceivableAllocationReplayConflictException;
import io.github.khaledshawki.eoc.operations.application.exception.ReceivableInvoiceAllocationCapacityExceededException;
import io.github.khaledshawki.eoc.operations.application.exception.ReceivableSettlementStateCorruptedException;
import io.github.khaledshawki.eoc.operations.application.model.authorization.OperationsActor;
import io.github.khaledshawki.eoc.operations.application.model.authorization.OperationsPermission;
import io.github.khaledshawki.eoc.operations.application.model.settlement.ReceivableAllocationResult;
import io.github.khaledshawki.eoc.operations.application.port.in.AllocateReceivablePaymentCommand;
import io.github.khaledshawki.eoc.operations.application.port.in.ReverseReceivableAllocationCommand;
import io.github.khaledshawki.eoc.operations.application.port.out.InvoiceRepository;
import io.github.khaledshawki.eoc.operations.application.port.out.OperationsAuthorizationPort;
import io.github.khaledshawki.eoc.operations.application.port.out.PaymentRepository;
import io.github.khaledshawki.eoc.operations.application.port.out.ReceivableSettlementMutationUnitOfWork;
import io.github.khaledshawki.eoc.operations.application.port.out.ReceivableSettlementRepository;
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
import io.github.khaledshawki.eoc.operations.domain.model.ReceivableSettlementId;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

class ReceivableSettlementServiceTest {

  private static final OperationsTenantId TENANT_ID =
      OperationsTenantId.of(UUID.fromString("00000000-0000-0000-0000-000000000001"));
  private static final OperationsTenantId OTHER_TENANT_ID =
      OperationsTenantId.of(UUID.fromString("00000000-0000-0000-0000-000000000099"));
  private static final BusinessPartnerId CUSTOMER_ID =
      BusinessPartnerId.of(UUID.fromString("00000000-0000-0000-0000-000000000010"));
  private static final BusinessPartnerId OTHER_CUSTOMER_ID =
      BusinessPartnerId.of(UUID.fromString("00000000-0000-0000-0000-000000000011"));
  private static final PaymentId PAYMENT_ID =
      PaymentId.of(UUID.fromString("00000000-0000-0000-0000-000000000020"));
  private static final PaymentId OTHER_PAYMENT_ID =
      PaymentId.of(UUID.fromString("00000000-0000-0000-0000-000000000021"));
  private static final InvoiceId INVOICE_ID =
      InvoiceId.of(UUID.fromString("00000000-0000-0000-0000-000000000030"));
  private static final InvoiceId OTHER_INVOICE_ID =
      InvoiceId.of(UUID.fromString("00000000-0000-0000-0000-000000000031"));
  private static final ReceivableAllocationId ALLOCATION_ID =
      ReceivableAllocationId.of(UUID.fromString("00000000-0000-0000-0000-000000000040"));
  private static final OperationsActor ACTOR = new OperationsActor("issuer", "subject");
  private static final CurrencyCode EUR = CurrencyCode.of("EUR");
  private static final CurrencyCode USD = CurrencyCode.of("USD");
  private static final Instant NOW = Instant.parse("2026-08-11T12:00:00Z");

  @Test
  void shouldCreateSettlementAndAllocateInsideScopedUnitOfWork() {
    Fixture fixture = fixture(payment(), invoice("100.00", "0.00", EUR, CUSTOMER_ID, false));
    AtomicReference<OperationsPermission> permission = new AtomicReference<>();
    fixture.authorization =
        (actor, tenantId, requestedPermission) -> {
          permission.set(requestedPermission);
          return true;
        };

    ReceivableAllocationResult result = fixture.service().allocate(allocate("40.00", INVOICE_ID));

    assertEquals(OperationsPermission.MANAGE_RECEIVABLE_SETTLEMENTS, permission.get());
    assertEquals(PAYMENT_ID, result.paymentId());
    assertEquals(ALLOCATION_ID, result.allocationId());
    assertEquals(INVOICE_ID, result.invoiceId());
    assertEquals(Money.of("40.00", EUR), result.amount());
    assertEquals(ReceivableAllocationState.ACTIVE, result.state());
    assertEquals(1, fixture.unitOfWork.calls.get());
    assertEquals(TENANT_ID, fixture.unitOfWork.tenantId.get());
    assertEquals(PAYMENT_ID, fixture.unitOfWork.paymentId.get());
    assertEquals(INVOICE_ID, fixture.unitOfWork.invoiceId.get());
    assertEquals(ALLOCATION_ID, fixture.unitOfWork.allocationId.get());
    assertEquals(1, fixture.settlements.saveCalls.get());
    assertTrue(fixture.unitOfWork.workWasInsideBoundary.get());
    assertEquals(1, fixture.eventOutbox.events.size());
    assertEquals(
        "operations.receivable-allocation.applied.v1",
        fixture.eventOutbox.events.getFirst().eventType());
  }

  @Test
  void shouldReuseExistingSettlementForSamePayment() {
    Fixture fixture = fixture(payment(), invoice("100.00", "0.00", EUR, CUSTOMER_ID, false));
    ReceivableSettlement existing = ReceivableSettlement.open(payment());
    ReceivableSettlementId existingId = existing.id();
    fixture.settlements.byPayment = Optional.of(existing);

    ReceivableAllocationResult result = fixture.service().allocate(allocate("25.00", INVOICE_ID));

    assertEquals(existingId, result.settlementId());
    assertEquals(1, fixture.settlements.saveCalls.get());
  }

  @Test
  void shouldReturnExactActiveReplayWithoutCanonicalReadsOrSave() {
    Payment payment = payment();
    Invoice invoice = invoice("100.00", "0.00", EUR, CUSTOMER_ID, false);
    ReceivableSettlement settlement = settlementWithActiveAllocation(payment, invoice, "40.00");
    Fixture fixture = fixture(null, null);
    fixture.payments.failOnRead = true;
    fixture.invoices.failOnRead = true;
    fixture.settlements.byAllocation = Optional.of(settlement);
    fixture.settlements.byPayment = Optional.of(settlement);

    ReceivableAllocationResult result = fixture.service().allocate(allocate("40.00", INVOICE_ID));

    assertEquals(ReceivableAllocationState.ACTIVE, result.state());
    assertEquals(0, fixture.payments.findCalls.get());
    assertEquals(0, fixture.invoices.findCalls.get());
    assertEquals(0, fixture.settlements.capacityCalls.get());
    assertEquals(0, fixture.settlements.saveCalls.get());
    assertTrue(fixture.eventOutbox.events.isEmpty());
  }

  @Test
  void shouldRejectAllocationIdentityReplayForAnotherPayment() {
    Payment payment = payment();
    Invoice invoice = invoice("100.00", "0.00", EUR, CUSTOMER_ID, false);
    ReceivableSettlement settlement = settlementWithActiveAllocation(payment, invoice, "40.00");
    Fixture fixture = fixture(null, null);
    fixture.settlements.byAllocation = Optional.of(settlement);

    AllocateReceivablePaymentCommand command =
        new AllocateReceivablePaymentCommand(
            ACTOR,
            TENANT_ID.value(),
            OTHER_PAYMENT_ID.value(),
            INVOICE_ID.value(),
            ALLOCATION_ID.value(),
            Money.of("40.00", EUR));

    assertThrows(
        ReceivableAllocationReplayConflictException.class,
        () -> fixture.service().allocate(command));
  }

  @Test
  void shouldRejectAllocationIdentityReplayForAnotherInvoiceOrAmount() {
    Payment payment = payment();
    Invoice invoice = invoice("100.00", "0.00", EUR, CUSTOMER_ID, false);
    ReceivableSettlement settlement = settlementWithActiveAllocation(payment, invoice, "40.00");
    Fixture fixture = fixture(null, null);
    fixture.settlements.byAllocation = Optional.of(settlement);

    AllocateReceivablePaymentCommand anotherInvoice =
        new AllocateReceivablePaymentCommand(
            ACTOR,
            TENANT_ID.value(),
            PAYMENT_ID.value(),
            OTHER_INVOICE_ID.value(),
            ALLOCATION_ID.value(),
            Money.of("40.00", EUR));
    AllocateReceivablePaymentCommand anotherAmount =
        new AllocateReceivablePaymentCommand(
            ACTOR,
            TENANT_ID.value(),
            PAYMENT_ID.value(),
            INVOICE_ID.value(),
            ALLOCATION_ID.value(),
            Money.of("41.00", EUR));

    assertThrows(
        ReceivableAllocationReplayConflictException.class,
        () -> fixture.service().allocate(anotherInvoice));
    assertThrows(
        ReceivableAllocationReplayConflictException.class,
        () -> fixture.service().allocate(anotherAmount));
  }

  @Test
  void shouldNeverReuseReversedAllocationIdentity() {
    Payment payment = payment();
    Invoice invoice = invoice("100.00", "0.00", EUR, CUSTOMER_ID, false);
    ReceivableSettlement settlement = settlementWithActiveAllocation(payment, invoice, "40.00");
    settlement.reverseAllocation(ALLOCATION_ID);
    Fixture fixture = fixture(null, null);
    fixture.settlements.byAllocation = Optional.of(settlement);
    fixture.settlements.byPayment = Optional.of(settlement);

    assertThrows(
        ReceivableAllocationReplayConflictException.class,
        () -> fixture.service().allocate(allocate("40.00", INVOICE_ID)));
  }

  @Test
  void shouldFailClosedBeforeUnitOfWorkAndRepositoriesWhenUnauthorized() {
    Fixture fixture = fixture(payment(), invoice("100.00", "0.00", EUR, CUSTOMER_ID, false));
    fixture.authorization = (actor, tenantId, permission) -> false;
    fixture.payments.failOnRead = true;
    fixture.invoices.failOnRead = true;
    fixture.settlements.failOnRead = true;

    assertThrows(
        OperationsAccessDeniedException.class,
        () -> fixture.service().allocate(allocate("10.00", INVOICE_ID)));
    assertEquals(0, fixture.unitOfWork.calls.get());
  }

  @Test
  void shouldRejectNewAllocationForReversedPayment() {
    Payment reversed = payment(PAYMENT_ID, TENANT_ID, CUSTOMER_ID, "100.00", EUR, true);
    Fixture fixture = fixture(reversed, invoice("100.00", "0.00", EUR, CUSTOMER_ID, false));

    assertThrows(
        IllegalArgumentException.class,
        () -> fixture.service().allocate(allocate("10.00", INVOICE_ID)));
    assertEquals(0, fixture.settlements.saveCalls.get());
  }

  @Test
  void shouldRejectNewAllocationForCancelledInvoice() {
    Fixture fixture = fixture(payment(), invoice("100.00", "0.00", EUR, CUSTOMER_ID, true));

    assertThrows(
        IllegalArgumentException.class,
        () -> fixture.service().allocate(allocate("10.00", INVOICE_ID)));
    assertEquals(0, fixture.settlements.saveCalls.get());
  }

  @Test
  void shouldFailWhenCanonicalPaymentOrInvoiceIsMissing() {
    Fixture missingPayment = fixture(null, invoice("100.00", "0.00", EUR, CUSTOMER_ID, false));
    assertThrows(
        PaymentNotFoundException.class,
        () -> missingPayment.service().allocate(allocate("10.00", INVOICE_ID)));

    Fixture missingInvoice = fixture(payment(), null);
    assertThrows(
        InvoiceNotFoundException.class,
        () -> missingInvoice.service().allocate(allocate("10.00", INVOICE_ID)));
  }

  @Test
  void shouldRejectAllocationThatExceedsRemainingPaymentCapacity() {
    Payment payment = payment();
    Invoice previousInvoice =
        invoice(OTHER_INVOICE_ID, TENANT_ID, CUSTOMER_ID, "100.00", "0.00", EUR, false);
    ReceivableSettlement settlement = ReceivableSettlement.open(payment);
    settlement.allocate(
        ReceivableAllocationId.generate(), payment, previousInvoice, Money.of("80.00", EUR));
    Fixture fixture = fixture(payment, invoice("100.00", "0.00", EUR, CUSTOMER_ID, false));
    fixture.settlements.byPayment = Optional.of(settlement);

    assertThrows(
        IllegalArgumentException.class,
        () -> fixture.service().allocate(allocate("30.00", INVOICE_ID)));
    assertEquals(0, fixture.settlements.saveCalls.get());
  }

  @Test
  void shouldRejectCrossPaymentInvoiceOverallocation() {
    Fixture fixture = fixture(payment(), invoice("100.00", "0.00", EUR, CUSTOMER_ID, false));
    fixture.settlements.globalAllocated = Money.of("80.00", EUR);

    ReceivableInvoiceAllocationCapacityExceededException exception =
        assertThrows(
            ReceivableInvoiceAllocationCapacityExceededException.class,
            () -> fixture.service().allocate(allocate("25.00", INVOICE_ID)));

    assertTrue(exception.getMessage().contains("available 20.00 EUR"));
    assertEquals(0, fixture.settlements.saveCalls.get());
  }

  @Test
  void shouldUseOriginalAmountNotSourcePaidAmountForLocalCapacity() {
    Fixture fixture = fixture(payment(), invoice("100.00", "90.00", EUR, CUSTOMER_ID, false));
    fixture.settlements.globalAllocated = Money.of("50.00", EUR);

    ReceivableAllocationResult result = fixture.service().allocate(allocate("50.00", INVOICE_ID));

    assertEquals(Money.of("50.00", EUR), result.amount());
    assertEquals(1, fixture.settlements.saveCalls.get());
  }

  @Test
  void shouldFailClosedWhenGlobalInvoiceAllocationTotalIsMissing() {
    Fixture fixture = fixture(payment(), invoice("100.00", "0.00", EUR, CUSTOMER_ID, false));
    fixture.settlements.globalAllocated = null;

    assertThrows(
        ReceivableSettlementStateCorruptedException.class,
        () -> fixture.service().allocate(allocate("10.00", INVOICE_ID)));
  }

  @Test
  void shouldFailClosedOnCorruptedGlobalInvoiceAllocationTotals() {
    Fixture wrongCurrency = fixture(payment(), invoice("100.00", "0.00", EUR, CUSTOMER_ID, false));
    wrongCurrency.settlements.globalAllocated = Money.of("10.00", USD);
    assertThrows(
        ReceivableSettlementStateCorruptedException.class,
        () -> wrongCurrency.service().allocate(allocate("10.00", INVOICE_ID)));

    Fixture negative = fixture(payment(), invoice("100.00", "0.00", EUR, CUSTOMER_ID, false));
    negative.settlements.globalAllocated = Money.of("-1.00", EUR);
    assertThrows(
        ReceivableSettlementStateCorruptedException.class,
        () -> negative.service().allocate(allocate("10.00", INVOICE_ID)));

    Fixture excessive = fixture(payment(), invoice("100.00", "0.00", EUR, CUSTOMER_ID, false));
    excessive.settlements.globalAllocated = Money.of("101.00", EUR);
    assertThrows(
        ReceivableSettlementStateCorruptedException.class,
        () -> excessive.service().allocate(allocate("10.00", INVOICE_ID)));
  }

  @Test
  void shouldFailClosedWhenExistingSettlementDoesNotMatchCanonicalPayment() {
    Payment canonical = payment();
    Fixture fixture = fixture(canonical, invoice("100.00", "0.00", EUR, CUSTOMER_ID, false));
    Payment differentCustomerPayment =
        payment(PAYMENT_ID, TENANT_ID, OTHER_CUSTOMER_ID, "100.00", EUR, false);
    fixture.settlements.byPayment =
        Optional.of(ReceivableSettlement.open(differentCustomerPayment));

    assertThrows(
        ReceivableSettlementStateCorruptedException.class,
        () -> fixture.service().allocate(allocate("10.00", INVOICE_ID)));
  }

  @Test
  void shouldFailClosedWhenAllocationLookupOmitsExistingSettlementAllocation() {
    Payment payment = payment();
    Invoice invoice = invoice("100.00", "0.00", EUR, CUSTOMER_ID, false);
    ReceivableSettlement settlement = settlementWithActiveAllocation(payment, invoice, "40.00");
    Fixture fixture = fixture(payment, invoice);
    fixture.settlements.byPayment = Optional.of(settlement);
    fixture.settlements.byAllocation = Optional.empty();

    assertThrows(
        ReceivableSettlementStateCorruptedException.class,
        () -> fixture.service().allocate(allocate("40.00", INVOICE_ID)));
  }

  @Test
  void shouldFailClosedWhenSettlementRepositoryReturnsNullFromSave() {
    Fixture fixture = fixture(payment(), invoice("100.00", "0.00", EUR, CUSTOMER_ID, false));
    fixture.settlements.saveTransform = settlement -> null;

    assertThrows(
        ReceivableSettlementStateCorruptedException.class,
        () -> fixture.service().allocate(allocate("10.00", INVOICE_ID)));
  }

  @Test
  void shouldFailClosedWhenPersistedSettlementChangesImmutableIdentityOrAllocation() {
    Fixture changedIdentity =
        fixture(payment(), invoice("100.00", "0.00", EUR, CUSTOMER_ID, false));
    changedIdentity.settlements.saveTransform =
        settlement ->
            ReceivableSettlement.reconstitute(
                ReceivableSettlementId.generate(),
                settlement.tenantId(),
                settlement.customerId(),
                settlement.paymentId(),
                settlement.currency(),
                settlement.allocations());
    assertThrows(
        ReceivableSettlementStateCorruptedException.class,
        () -> changedIdentity.service().allocate(allocate("10.00", INVOICE_ID)));

    Fixture changedAllocation =
        fixture(payment(), invoice("100.00", "0.00", EUR, CUSTOMER_ID, false));
    changedAllocation.settlements.saveTransform =
        settlement ->
            ReceivableSettlement.reconstitute(
                settlement.id(),
                settlement.tenantId(),
                settlement.customerId(),
                settlement.paymentId(),
                settlement.currency(),
                settlement.allocations().stream()
                    .map(
                        allocation ->
                            allocation.id().equals(ALLOCATION_ID)
                                ? ReceivableAllocation.active(
                                    allocation.id(), allocation.invoiceId(), Money.of("11.00", EUR))
                                : allocation)
                    .toList());
    assertThrows(
        ReceivableSettlementStateCorruptedException.class,
        () -> changedAllocation.service().allocate(allocate("10.00", INVOICE_ID)));
  }

  @Test
  void shouldReverseActiveAllocationAndPreserveHistory() {
    Payment payment = payment();
    Invoice invoice = invoice("100.00", "0.00", EUR, CUSTOMER_ID, false);
    ReceivableSettlement settlement = settlementWithActiveAllocation(payment, invoice, "40.00");
    Fixture fixture = fixture(payment, invoice);
    fixture.settlements.byAllocation = Optional.of(settlement);
    fixture.settlements.byPayment = Optional.of(settlement);

    ReceivableAllocationResult result = fixture.service().reverse(reverse(INVOICE_ID));

    assertEquals(ReceivableAllocationState.REVERSED, result.state());
    assertEquals(1, fixture.settlements.saveCalls.get());
    assertEquals(1, settlement.allocations().size());
    assertFalse(settlement.allocations().get(0).active());
    assertEquals(1, fixture.eventOutbox.events.size());
    assertEquals(
        "operations.receivable-allocation.reversed.v1",
        fixture.eventOutbox.events.getFirst().eventType());
  }

  @Test
  void shouldTreatRepeatedReversalAsIdempotentWithoutSavingAgain() {
    Payment payment = payment();
    Invoice invoice = invoice("100.00", "0.00", EUR, CUSTOMER_ID, false);
    ReceivableSettlement settlement = settlementWithActiveAllocation(payment, invoice, "40.00");
    settlement.reverseAllocation(ALLOCATION_ID);
    Fixture fixture = fixture(payment, invoice);
    fixture.settlements.byAllocation = Optional.of(settlement);
    fixture.settlements.byPayment = Optional.of(settlement);

    ReceivableAllocationResult result = fixture.service().reverse(reverse(INVOICE_ID));

    assertEquals(ReceivableAllocationState.REVERSED, result.state());
    assertEquals(0, fixture.settlements.saveCalls.get());
    assertTrue(fixture.eventOutbox.events.isEmpty());
  }

  @Test
  void shouldAllowReversalWithoutReadingChangedCanonicalPaymentOrInvoice() {
    Payment payment = payment();
    Invoice invoice = invoice("100.00", "0.00", EUR, CUSTOMER_ID, false);
    ReceivableSettlement settlement = settlementWithActiveAllocation(payment, invoice, "40.00");
    Fixture fixture = fixture(null, null);
    fixture.payments.failOnRead = true;
    fixture.invoices.failOnRead = true;
    fixture.settlements.byAllocation = Optional.of(settlement);
    fixture.settlements.byPayment = Optional.of(settlement);

    ReceivableAllocationResult result = fixture.service().reverse(reverse(INVOICE_ID));

    assertEquals(ReceivableAllocationState.REVERSED, result.state());
    assertEquals(0, fixture.payments.findCalls.get());
    assertEquals(0, fixture.invoices.findCalls.get());
  }

  @Test
  void shouldFailClosedBeforeReverseUnitOfWorkWhenUnauthorized() {
    Fixture fixture = fixture(null, null);
    fixture.authorization = (actor, tenantId, permission) -> false;
    fixture.settlements.failOnRead = true;

    assertThrows(
        OperationsAccessDeniedException.class,
        () -> fixture.service().reverse(reverse(INVOICE_ID)));
    assertEquals(0, fixture.unitOfWork.calls.get());
  }

  @Test
  void shouldFailWhenReversingUnknownAllocation() {
    Fixture fixture = fixture(payment(), invoice("100.00", "0.00", EUR, CUSTOMER_ID, false));

    assertThrows(
        ReceivableAllocationNotFoundException.class,
        () -> fixture.service().reverse(reverse(INVOICE_ID)));
  }

  @Test
  void shouldRejectReverseCommandThatChangesPaymentOrInvoiceIdentity() {
    Payment payment = payment();
    Invoice invoice = invoice("100.00", "0.00", EUR, CUSTOMER_ID, false);
    ReceivableSettlement settlement = settlementWithActiveAllocation(payment, invoice, "40.00");

    Fixture wrongPayment = fixture(null, null);
    wrongPayment.settlements.byAllocation = Optional.of(settlement);
    ReverseReceivableAllocationCommand anotherPayment =
        new ReverseReceivableAllocationCommand(
            ACTOR,
            TENANT_ID.value(),
            OTHER_PAYMENT_ID.value(),
            INVOICE_ID.value(),
            ALLOCATION_ID.value());
    assertThrows(
        ReceivableAllocationReplayConflictException.class,
        () -> wrongPayment.service().reverse(anotherPayment));

    Fixture wrongInvoice = fixture(null, null);
    wrongInvoice.settlements.byAllocation = Optional.of(settlement);
    ReverseReceivableAllocationCommand anotherInvoice =
        new ReverseReceivableAllocationCommand(
            ACTOR,
            TENANT_ID.value(),
            PAYMENT_ID.value(),
            OTHER_INVOICE_ID.value(),
            ALLOCATION_ID.value());
    assertThrows(
        ReceivableAllocationReplayConflictException.class,
        () -> wrongInvoice.service().reverse(anotherInvoice));
  }

  @Test
  void shouldFailClosedWhenAllocationAndPaymentLookupsDisagree() {
    Payment payment = payment();
    Invoice invoice = invoice("100.00", "0.00", EUR, CUSTOMER_ID, false);
    ReceivableSettlement allocationOwner =
        settlementWithActiveAllocation(payment, invoice, "40.00");
    ReceivableSettlement different = ReceivableSettlement.open(payment);
    assertNotEquals(allocationOwner.id(), different.id());
    Fixture fixture = fixture(null, null);
    fixture.settlements.byAllocation = Optional.of(allocationOwner);
    fixture.settlements.byPayment = Optional.of(different);

    assertThrows(
        ReceivableSettlementStateCorruptedException.class,
        () -> fixture.service().reverse(reverse(INVOICE_ID)));
  }

  @Test
  void shouldRejectTenantScopedLookupReturningAnotherTenantSettlement() {
    Payment otherTenantPayment =
        payment(PAYMENT_ID, OTHER_TENANT_ID, CUSTOMER_ID, "100.00", EUR, false);
    Invoice otherTenantInvoice =
        invoice(INVOICE_ID, OTHER_TENANT_ID, CUSTOMER_ID, "100.00", "0.00", EUR, false);
    ReceivableSettlement settlement =
        settlementWithActiveAllocation(otherTenantPayment, otherTenantInvoice, "40.00");
    Fixture fixture = fixture(null, null);
    fixture.settlements.byAllocation = Optional.of(settlement);

    assertThrows(
        ReceivableSettlementStateCorruptedException.class,
        () -> fixture.service().reverse(reverse(INVOICE_ID)));
  }

  @Test
  void shouldRejectNullCommands() {
    Fixture fixture = fixture(payment(), invoice("100.00", "0.00", EUR, CUSTOMER_ID, false));

    assertThrows(NullPointerException.class, () -> fixture.service().allocate(null));
    assertThrows(NullPointerException.class, () -> fixture.service().reverse(null));
  }

  private static AllocateReceivablePaymentCommand allocate(String amount, InvoiceId invoiceId) {
    return new AllocateReceivablePaymentCommand(
        ACTOR,
        TENANT_ID.value(),
        PAYMENT_ID.value(),
        invoiceId.value(),
        ALLOCATION_ID.value(),
        Money.of(amount, EUR));
  }

  private static ReverseReceivableAllocationCommand reverse(InvoiceId invoiceId) {
    return new ReverseReceivableAllocationCommand(
        ACTOR, TENANT_ID.value(), PAYMENT_ID.value(), invoiceId.value(), ALLOCATION_ID.value());
  }

  private static ReceivableSettlement settlementWithActiveAllocation(
      Payment payment, Invoice invoice, String amount) {
    ReceivableSettlement settlement = ReceivableSettlement.open(payment);
    settlement.allocate(ALLOCATION_ID, payment, invoice, Money.of(amount, EUR));
    return settlement;
  }

  private static Fixture fixture(Payment payment, Invoice invoice) {
    return new Fixture(payment, invoice);
  }

  private static Payment payment() {
    return payment(PAYMENT_ID, TENANT_ID, CUSTOMER_ID, "100.00", EUR, false);
  }

  private static Payment payment(
      PaymentId id,
      OperationsTenantId tenantId,
      BusinessPartnerId customerId,
      String amount,
      CurrencyCode currency,
      boolean reversed) {
    return Payment.reconstitute(
        id, tenantId, customerId, Money.of(amount, currency), LocalDate.of(2026, 8, 8), reversed);
  }

  private static Invoice invoice(
      String original,
      String paid,
      CurrencyCode currency,
      BusinessPartnerId customerId,
      boolean cancelled) {
    return invoice(INVOICE_ID, TENANT_ID, customerId, original, paid, currency, cancelled);
  }

  private static Invoice invoice(
      InvoiceId id,
      OperationsTenantId tenantId,
      BusinessPartnerId customerId,
      String original,
      String paid,
      CurrencyCode currency,
      boolean cancelled) {
    return Invoice.reconstitute(
        id,
        tenantId,
        customerId,
        new InvoiceNumber("INV-" + id.value()),
        Money.of(original, currency),
        Money.of(paid, currency),
        LocalDate.of(2026, 8, 1),
        LocalDate.of(2026, 8, 31),
        cancelled);
  }

  private static final class Fixture {

    private final FakePaymentRepository payments;
    private final FakeInvoiceRepository invoices;
    private final FakeSettlementRepository settlements = new FakeSettlementRepository();
    private final FakeUnitOfWork unitOfWork = new FakeUnitOfWork();
    private final RecordingOperationsIntegrationEventOutbox eventOutbox =
        new RecordingOperationsIntegrationEventOutbox();
    private OperationsAuthorizationPort authorization = (actor, tenantId, permission) -> true;

    private Fixture(Payment payment, Invoice invoice) {
      payments = new FakePaymentRepository(payment);
      invoices = new FakeInvoiceRepository(invoice);
    }

    private ReceivableSettlementService service() {
      return new ReceivableSettlementService(
          payments,
          invoices,
          settlements,
          unitOfWork,
          authorization,
          eventOutbox,
          Clock.fixed(NOW, ZoneOffset.UTC));
    }
  }

  private static final class FakePaymentRepository implements PaymentRepository {

    private final Payment payment;
    private final AtomicInteger findCalls = new AtomicInteger();
    private boolean failOnRead;

    private FakePaymentRepository(Payment payment) {
      this.payment = payment;
    }

    @Override
    public Payment save(Payment payment) {
      throw new AssertionError("Payment save must not be called by settlement service");
    }

    @Override
    public Optional<Payment> findById(OperationsTenantId tenantId, PaymentId paymentId) {
      if (failOnRead) {
        throw new AssertionError("Payment repository must not be read");
      }
      findCalls.incrementAndGet();
      assertEquals(TENANT_ID, tenantId);
      assertEquals(PAYMENT_ID, paymentId);
      return Optional.ofNullable(payment);
    }
  }

  private static final class FakeInvoiceRepository implements InvoiceRepository {

    private final Invoice invoice;
    private final AtomicInteger findCalls = new AtomicInteger();
    private boolean failOnRead;

    private FakeInvoiceRepository(Invoice invoice) {
      this.invoice = invoice;
    }

    @Override
    public Invoice save(Invoice invoice) {
      throw new AssertionError("Invoice save must not be called by settlement service");
    }

    @Override
    public Optional<Invoice> findById(OperationsTenantId tenantId, InvoiceId invoiceId) {
      if (failOnRead) {
        throw new AssertionError("Invoice repository must not be read");
      }
      findCalls.incrementAndGet();
      assertEquals(TENANT_ID, tenantId);
      assertEquals(INVOICE_ID, invoiceId);
      return Optional.ofNullable(invoice);
    }
  }

  private static final class FakeSettlementRepository implements ReceivableSettlementRepository {

    private Optional<ReceivableSettlement> byPayment = Optional.empty();
    private Optional<ReceivableSettlement> byAllocation = Optional.empty();
    private Money globalAllocated = Money.zero(EUR);
    private final AtomicInteger saveCalls = new AtomicInteger();
    private final AtomicInteger capacityCalls = new AtomicInteger();
    private boolean failOnRead;
    private java.util.function.UnaryOperator<ReceivableSettlement> saveTransform =
        settlement -> settlement;

    @Override
    public ReceivableSettlement save(ReceivableSettlement settlement) {
      saveCalls.incrementAndGet();
      ReceivableSettlement persisted = saveTransform.apply(settlement);
      byPayment = Optional.ofNullable(persisted);
      byAllocation = Optional.ofNullable(persisted);
      return persisted;
    }

    @Override
    public Optional<ReceivableSettlement> findByPaymentId(
        OperationsTenantId tenantId, PaymentId paymentId) {
      failIfRequired();
      return byPayment;
    }

    @Override
    public Optional<ReceivableSettlement> findByAllocationId(
        OperationsTenantId tenantId, ReceivableAllocationId allocationId) {
      failIfRequired();
      return byAllocation;
    }

    @Override
    public Money activeAllocatedAmountForInvoice(
        OperationsTenantId tenantId, InvoiceId invoiceId, CurrencyCode currency) {
      failIfRequired();
      capacityCalls.incrementAndGet();
      return globalAllocated;
    }

    private void failIfRequired() {
      if (failOnRead) {
        throw new AssertionError("Settlement repository must not be read");
      }
    }
  }

  private static final class FakeUnitOfWork implements ReceivableSettlementMutationUnitOfWork {

    private final AtomicInteger calls = new AtomicInteger();
    private final AtomicReference<OperationsTenantId> tenantId = new AtomicReference<>();
    private final AtomicReference<PaymentId> paymentId = new AtomicReference<>();
    private final AtomicReference<InvoiceId> invoiceId = new AtomicReference<>();
    private final AtomicReference<ReceivableAllocationId> allocationId = new AtomicReference<>();
    private final AtomicBoolean workWasInsideBoundary = new AtomicBoolean();

    @Override
    public ReceivableAllocationResult execute(
        OperationsTenantId tenantId,
        PaymentId paymentId,
        InvoiceId invoiceId,
        ReceivableAllocationId allocationId,
        Supplier<ReceivableAllocationResult> work) {
      calls.incrementAndGet();
      this.tenantId.set(tenantId);
      this.paymentId.set(paymentId);
      this.invoiceId.set(invoiceId);
      this.allocationId.set(allocationId);
      workWasInsideBoundary.set(true);
      return work.get();
    }
  }
}
