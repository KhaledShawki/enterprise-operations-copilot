package io.github.khaledshawki.eoc.operations.application.service;

import io.github.khaledshawki.eoc.operations.application.exception.InvoiceNotFoundException;
import io.github.khaledshawki.eoc.operations.application.exception.OperationsAccessDeniedException;
import io.github.khaledshawki.eoc.operations.application.exception.PaymentNotFoundException;
import io.github.khaledshawki.eoc.operations.application.exception.ReceivableAllocationNotFoundException;
import io.github.khaledshawki.eoc.operations.application.exception.ReceivableAllocationReplayConflictException;
import io.github.khaledshawki.eoc.operations.application.exception.ReceivableInvoiceAllocationCapacityExceededException;
import io.github.khaledshawki.eoc.operations.application.exception.ReceivableSettlementStateCorruptedException;
import io.github.khaledshawki.eoc.operations.application.model.authorization.OperationsActor;
import io.github.khaledshawki.eoc.operations.application.model.authorization.OperationsPermission;
import io.github.khaledshawki.eoc.operations.application.model.event.OperationsIntegrationEventFactory;
import io.github.khaledshawki.eoc.operations.application.model.settlement.ReceivableAllocationResult;
import io.github.khaledshawki.eoc.operations.application.port.in.AllocateReceivablePaymentCommand;
import io.github.khaledshawki.eoc.operations.application.port.in.AllocateReceivablePaymentUseCase;
import io.github.khaledshawki.eoc.operations.application.port.in.ReverseReceivableAllocationCommand;
import io.github.khaledshawki.eoc.operations.application.port.in.ReverseReceivableAllocationUseCase;
import io.github.khaledshawki.eoc.operations.application.port.out.InvoiceRepository;
import io.github.khaledshawki.eoc.operations.application.port.out.OperationsAuthorizationPort;
import io.github.khaledshawki.eoc.operations.application.port.out.OperationsIntegrationEventOutbox;
import io.github.khaledshawki.eoc.operations.application.port.out.PaymentRepository;
import io.github.khaledshawki.eoc.operations.application.port.out.ReceivableSettlementMutationUnitOfWork;
import io.github.khaledshawki.eoc.operations.application.port.out.ReceivableSettlementRepository;
import io.github.khaledshawki.eoc.operations.domain.model.Invoice;
import io.github.khaledshawki.eoc.operations.domain.model.InvoiceId;
import io.github.khaledshawki.eoc.operations.domain.model.Money;
import io.github.khaledshawki.eoc.operations.domain.model.OperationsTenantId;
import io.github.khaledshawki.eoc.operations.domain.model.Payment;
import io.github.khaledshawki.eoc.operations.domain.model.PaymentId;
import io.github.khaledshawki.eoc.operations.domain.model.ReceivableAllocation;
import io.github.khaledshawki.eoc.operations.domain.model.ReceivableAllocationId;
import io.github.khaledshawki.eoc.operations.domain.model.ReceivableSettlement;
import java.time.Clock;
import java.util.Objects;
import java.util.Optional;

/**
 * Coordinates local Payment-to-Invoice cash application. Source-authoritative Invoice paid amounts
 * are never mutated here; cross-Payment local capacity is coordinated through the settlement ports.
 */
public final class ReceivableSettlementService
    implements AllocateReceivablePaymentUseCase, ReverseReceivableAllocationUseCase {

  private final PaymentRepository paymentRepository;
  private final InvoiceRepository invoiceRepository;
  private final ReceivableSettlementRepository settlementRepository;
  private final ReceivableSettlementMutationUnitOfWork unitOfWork;
  private final OperationsAuthorizationPort authorizationPort;
  private final OperationsIntegrationEventOutbox eventOutbox;
  private final Clock clock;

  public ReceivableSettlementService(
      PaymentRepository paymentRepository,
      InvoiceRepository invoiceRepository,
      ReceivableSettlementRepository settlementRepository,
      ReceivableSettlementMutationUnitOfWork unitOfWork,
      OperationsAuthorizationPort authorizationPort,
      OperationsIntegrationEventOutbox eventOutbox,
      Clock clock) {
    this.paymentRepository =
        Objects.requireNonNull(paymentRepository, "Payment repository cannot be null");
    this.invoiceRepository =
        Objects.requireNonNull(invoiceRepository, "Invoice repository cannot be null");
    this.settlementRepository =
        Objects.requireNonNull(
            settlementRepository, "Receivable settlement repository cannot be null");
    this.unitOfWork =
        Objects.requireNonNull(
            unitOfWork, "Receivable settlement mutation unit of work cannot be null");
    this.authorizationPort =
        Objects.requireNonNull(authorizationPort, "Operations authorization port cannot be null");
    this.eventOutbox =
        Objects.requireNonNull(eventOutbox, "Operations event outbox cannot be null");
    this.clock = Objects.requireNonNull(clock, "Clock cannot be null");
  }

  @Override
  public ReceivableAllocationResult allocate(AllocateReceivablePaymentCommand command) {
    Objects.requireNonNull(command, "Allocate receivable payment command cannot be null");
    OperationsTenantId tenantId = OperationsTenantId.of(command.tenantId());
    authorize(command.actor(), tenantId);
    PaymentId paymentId = PaymentId.of(command.paymentId());
    InvoiceId invoiceId = InvoiceId.of(command.invoiceId());
    ReceivableAllocationId allocationId = ReceivableAllocationId.of(command.allocationId());

    return unitOfWork.execute(
        tenantId,
        paymentId,
        invoiceId,
        allocationId,
        () ->
            allocateWithinUnitOfWork(
                tenantId, paymentId, invoiceId, allocationId, command.amount()));
  }

  @Override
  public ReceivableAllocationResult reverse(ReverseReceivableAllocationCommand command) {
    Objects.requireNonNull(command, "Reverse receivable allocation command cannot be null");
    OperationsTenantId tenantId = OperationsTenantId.of(command.tenantId());
    authorize(command.actor(), tenantId);
    PaymentId paymentId = PaymentId.of(command.paymentId());
    InvoiceId invoiceId = InvoiceId.of(command.invoiceId());
    ReceivableAllocationId allocationId = ReceivableAllocationId.of(command.allocationId());

    return unitOfWork.execute(
        tenantId,
        paymentId,
        invoiceId,
        allocationId,
        () -> reverseWithinUnitOfWork(tenantId, paymentId, invoiceId, allocationId));
  }

  private ReceivableAllocationResult allocateWithinUnitOfWork(
      OperationsTenantId tenantId,
      PaymentId paymentId,
      InvoiceId invoiceId,
      ReceivableAllocationId allocationId,
      Money amount) {
    Optional<ReceivableSettlement> allocationOwner =
        settlementRepository.findByAllocationId(tenantId, allocationId);
    if (allocationOwner.isPresent()) {
      return resolveAllocationReplay(
          tenantId, paymentId, invoiceId, allocationId, amount, allocationOwner.orElseThrow());
    }

    Payment payment =
        paymentRepository
            .findById(tenantId, paymentId)
            .orElseThrow(() -> new PaymentNotFoundException(tenantId, paymentId));
    Invoice invoice =
        invoiceRepository
            .findById(tenantId, invoiceId)
            .orElseThrow(() -> new InvoiceNotFoundException(tenantId, invoiceId));

    ReceivableSettlement settlement =
        settlementRepository
            .findByPaymentId(tenantId, paymentId)
            .map(existing -> validateSettlementForPayment(tenantId, payment, existing))
            .orElseGet(() -> ReceivableSettlement.open(payment));
    if (findAllocation(settlement, allocationId).isPresent()) {
      throw corrupted(
          "Receivable allocation lookup omitted an allocation already present in the payment settlement");
    }

    validateGlobalInvoiceCapacity(tenantId, invoice, amount);
    ReceivableAllocation allocation = settlement.allocate(allocationId, payment, invoice, amount);
    ReceivableSettlement persisted = persistAndValidate(settlement, allocation);
    ReceivableAllocationResult result =
        ReceivableAllocationResult.from(
            persisted,
            findAllocation(persisted, allocationId)
                .orElseThrow(
                    () -> corrupted("Persisted receivable settlement omitted the new allocation")));
    eventOutbox.append(
        OperationsIntegrationEventFactory.pendingReceivableAllocationApplied(
            tenantId, result, clock.instant()));
    return result;
  }

  private ReceivableAllocationResult resolveAllocationReplay(
      OperationsTenantId tenantId,
      PaymentId paymentId,
      InvoiceId invoiceId,
      ReceivableAllocationId allocationId,
      Money amount,
      ReceivableSettlement owner) {
    validateTenant(owner, tenantId);
    ReceivableAllocation allocation =
        findAllocation(owner, allocationId)
            .orElseThrow(
                () -> corrupted("Allocation lookup returned a settlement without the allocation"));
    requireSameMutation(paymentId, invoiceId, amount, owner, allocation);
    validatePaymentLookupConsistency(tenantId, paymentId, owner, allocation);
    if (!allocation.active()) {
      throw new ReceivableAllocationReplayConflictException(
          allocationId, "a reversed allocation identity cannot be reused");
    }
    return ReceivableAllocationResult.from(owner, allocation);
  }

  private ReceivableAllocationResult reverseWithinUnitOfWork(
      OperationsTenantId tenantId,
      PaymentId paymentId,
      InvoiceId invoiceId,
      ReceivableAllocationId allocationId) {
    ReceivableSettlement settlement =
        settlementRepository
            .findByAllocationId(tenantId, allocationId)
            .orElseThrow(() -> new ReceivableAllocationNotFoundException(tenantId, allocationId));
    validateTenant(settlement, tenantId);
    ReceivableAllocation allocation =
        findAllocation(settlement, allocationId)
            .orElseThrow(
                () -> corrupted("Allocation lookup returned a settlement without the allocation"));
    requireSameMutation(paymentId, invoiceId, settlement, allocation);
    validatePaymentLookupConsistency(tenantId, paymentId, settlement, allocation);

    if (!allocation.active()) {
      return ReceivableAllocationResult.from(settlement, allocation);
    }

    ReceivableAllocation reversed = settlement.reverseAllocation(allocationId);
    ReceivableSettlement persisted = persistAndValidate(settlement, reversed);
    ReceivableAllocationResult result =
        ReceivableAllocationResult.from(
            persisted,
            findAllocation(persisted, allocationId)
                .orElseThrow(
                    () ->
                        corrupted(
                            "Persisted receivable settlement omitted the reversed allocation")));
    eventOutbox.append(
        OperationsIntegrationEventFactory.pendingReceivableAllocationReversed(
            tenantId, result, clock.instant()));
    return result;
  }

  private void validateGlobalInvoiceCapacity(
      OperationsTenantId tenantId, Invoice invoice, Money requestedAmount) {
    Money allocated =
        settlementRepository.activeAllocatedAmountForInvoice(
            tenantId, invoice.id(), invoice.originalAmount().currency());
    if (allocated == null) {
      throw corrupted("Active Invoice allocation total cannot be null");
    }
    if (!allocated.currency().equals(invoice.originalAmount().currency())) {
      throw corrupted("Active Invoice allocation total uses the wrong currency");
    }
    if (allocated.isNegative()) {
      throw corrupted("Active Invoice allocation total cannot be negative");
    }
    if (allocated.compareTo(invoice.originalAmount()) > 0) {
      throw corrupted("Active Invoice allocations exceed the current Invoice original amount");
    }
    if (!requestedAmount.currency().equals(invoice.originalAmount().currency())) {
      throw new IllegalArgumentException("Allocation currency does not match Invoice currency");
    }
    if (!requestedAmount.isPositive()) {
      throw new IllegalArgumentException("Receivable allocation amount must be positive");
    }

    Money available = invoice.originalAmount().subtract(allocated);
    if (requestedAmount.compareTo(available) > 0) {
      throw new ReceivableInvoiceAllocationCapacityExceededException(
          invoice.id(), requestedAmount, available);
    }
  }

  private ReceivableSettlement validateSettlementForPayment(
      OperationsTenantId tenantId, Payment payment, ReceivableSettlement settlement) {
    validateTenant(settlement, tenantId);
    if (!settlement.paymentId().equals(payment.id())) {
      throw corrupted("Payment settlement lookup returned a settlement for another Payment");
    }
    if (!settlement.customerId().equals(payment.customerId())) {
      throw corrupted("Payment settlement customer no longer matches the canonical Payment");
    }
    if (!settlement.currency().equals(payment.amount().currency())) {
      throw corrupted("Payment settlement currency no longer matches the canonical Payment");
    }
    return settlement;
  }

  private void validatePaymentLookupConsistency(
      OperationsTenantId tenantId,
      PaymentId paymentId,
      ReceivableSettlement expected,
      ReceivableAllocation expectedAllocation) {
    ReceivableSettlement byPayment =
        settlementRepository
            .findByPaymentId(tenantId, paymentId)
            .orElseThrow(
                () -> corrupted("Allocation settlement is missing from the Payment lookup"));
    validateTenant(byPayment, tenantId);
    if (!byPayment.id().equals(expected.id())
        || !byPayment.paymentId().equals(expected.paymentId())
        || !byPayment.customerId().equals(expected.customerId())
        || !byPayment.currency().equals(expected.currency())) {
      throw corrupted(
          "Allocation and Payment lookups disagree on settlement identity or ownership");
    }
    ReceivableAllocation byPaymentAllocation =
        findAllocation(byPayment, expectedAllocation.id())
            .orElseThrow(
                () -> corrupted("Payment settlement lookup omitted the expected allocation"));
    if (!byPaymentAllocation.equals(expectedAllocation)) {
      throw corrupted("Allocation and Payment lookups disagree on allocation state");
    }
  }

  private ReceivableSettlement persistAndValidate(
      ReceivableSettlement settlement, ReceivableAllocation expectedAllocation) {
    ReceivableSettlement persisted = settlementRepository.save(settlement);
    if (persisted == null) {
      throw corrupted("Persisted receivable settlement cannot be null");
    }
    if (!persisted.id().equals(settlement.id())
        || !persisted.tenantId().equals(settlement.tenantId())
        || !persisted.paymentId().equals(settlement.paymentId())
        || !persisted.customerId().equals(settlement.customerId())
        || !persisted.currency().equals(settlement.currency())) {
      throw corrupted("Persisted receivable settlement changed immutable settlement identity");
    }
    ReceivableAllocation persistedAllocation =
        findAllocation(persisted, expectedAllocation.id())
            .orElseThrow(
                () -> corrupted("Persisted receivable settlement omitted the expected allocation"));
    if (!persistedAllocation.equals(expectedAllocation)) {
      throw corrupted("Persisted receivable allocation differs from the requested mutation");
    }
    return persisted;
  }

  private static void requireSameMutation(
      PaymentId paymentId,
      InvoiceId invoiceId,
      Money amount,
      ReceivableSettlement settlement,
      ReceivableAllocation allocation) {
    if (!settlement.paymentId().equals(paymentId)) {
      throw new ReceivableAllocationReplayConflictException(
          allocation.id(), "allocation identity belongs to another Payment");
    }
    if (!allocation.invoiceId().equals(invoiceId)) {
      throw new ReceivableAllocationReplayConflictException(
          allocation.id(), "allocation identity belongs to another Invoice");
    }
    if (!allocation.amount().equals(amount)) {
      throw new ReceivableAllocationReplayConflictException(
          allocation.id(), "allocation identity was already used with another amount");
    }
  }

  private static void requireSameMutation(
      PaymentId paymentId,
      InvoiceId invoiceId,
      ReceivableSettlement settlement,
      ReceivableAllocation allocation) {
    if (!settlement.paymentId().equals(paymentId)) {
      throw new ReceivableAllocationReplayConflictException(
          allocation.id(), "allocation identity belongs to another Payment");
    }
    if (!allocation.invoiceId().equals(invoiceId)) {
      throw new ReceivableAllocationReplayConflictException(
          allocation.id(), "allocation identity belongs to another Invoice");
    }
  }

  private static Optional<ReceivableAllocation> findAllocation(
      ReceivableSettlement settlement, ReceivableAllocationId allocationId) {
    return settlement.allocations().stream()
        .filter(allocation -> allocation.id().equals(allocationId))
        .findFirst();
  }

  private static void validateTenant(ReceivableSettlement settlement, OperationsTenantId tenantId) {
    if (!settlement.tenantId().equals(tenantId)) {
      throw corrupted("Tenant-scoped settlement lookup returned another tenant's settlement");
    }
  }

  private void authorize(OperationsActor actor, OperationsTenantId tenantId) {
    if (!authorizationPort.hasPermission(
        actor, tenantId, OperationsPermission.MANAGE_RECEIVABLE_SETTLEMENTS)) {
      throw new OperationsAccessDeniedException(
          tenantId, OperationsPermission.MANAGE_RECEIVABLE_SETTLEMENTS);
    }
  }

  private static ReceivableSettlementStateCorruptedException corrupted(String detail) {
    return new ReceivableSettlementStateCorruptedException(detail);
  }
}
