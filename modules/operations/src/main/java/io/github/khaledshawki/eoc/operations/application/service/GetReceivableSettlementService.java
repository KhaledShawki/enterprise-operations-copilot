package io.github.khaledshawki.eoc.operations.application.service;

import io.github.khaledshawki.eoc.operations.application.exception.OperationsAccessDeniedException;
import io.github.khaledshawki.eoc.operations.application.exception.PaymentNotFoundException;
import io.github.khaledshawki.eoc.operations.application.exception.ReceivableSettlementStateCorruptedException;
import io.github.khaledshawki.eoc.operations.application.model.authorization.OperationsPermission;
import io.github.khaledshawki.eoc.operations.application.model.settlement.ReceivableAllocationResult;
import io.github.khaledshawki.eoc.operations.application.port.in.GetReceivableSettlementQuery;
import io.github.khaledshawki.eoc.operations.application.port.in.GetReceivableSettlementUseCase;
import io.github.khaledshawki.eoc.operations.application.port.in.PaymentResult;
import io.github.khaledshawki.eoc.operations.application.port.in.ReceivableSettlementResult;
import io.github.khaledshawki.eoc.operations.application.port.out.OperationsAuthorizationPort;
import io.github.khaledshawki.eoc.operations.application.port.out.PaymentRepository;
import io.github.khaledshawki.eoc.operations.application.port.out.ReceivableSettlementRepository;
import io.github.khaledshawki.eoc.operations.domain.model.Money;
import io.github.khaledshawki.eoc.operations.domain.model.OperationsTenantId;
import io.github.khaledshawki.eoc.operations.domain.model.Payment;
import io.github.khaledshawki.eoc.operations.domain.model.PaymentId;
import io.github.khaledshawki.eoc.operations.domain.model.ReceivableSettlement;
import io.github.khaledshawki.eoc.operations.domain.model.ReceivableSettlementId;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class GetReceivableSettlementService implements GetReceivableSettlementUseCase {

  private final PaymentRepository paymentRepository;
  private final ReceivableSettlementRepository settlementRepository;
  private final OperationsAuthorizationPort authorizationPort;

  public GetReceivableSettlementService(
      PaymentRepository paymentRepository,
      ReceivableSettlementRepository settlementRepository,
      OperationsAuthorizationPort authorizationPort) {
    this.paymentRepository =
        Objects.requireNonNull(paymentRepository, "Payment repository cannot be null");
    this.settlementRepository =
        Objects.requireNonNull(
            settlementRepository, "Receivable settlement repository cannot be null");
    this.authorizationPort =
        Objects.requireNonNull(authorizationPort, "Operations authorization port cannot be null");
  }

  @Override
  public ReceivableSettlementResult get(GetReceivableSettlementQuery query) {
    Objects.requireNonNull(query, "Receivable settlement query cannot be null");
    OperationsTenantId tenantId = OperationsTenantId.of(query.tenantId());
    authorize(query, tenantId);
    PaymentId paymentId = PaymentId.of(query.paymentId());

    Optional<Payment> paymentLookup = paymentRepository.findById(tenantId, paymentId);
    if (paymentLookup == null) {
      throw corrupted("Payment repository returned a null lookup result");
    }
    Payment payment =
        paymentLookup.orElseThrow(() -> new PaymentNotFoundException(tenantId, paymentId));

    Optional<ReceivableSettlement> settlementLookup =
        settlementRepository.findByPaymentId(tenantId, paymentId);
    if (settlementLookup == null) {
      throw corrupted("Receivable settlement repository returned a null lookup result");
    }
    if (settlementLookup.isEmpty()) {
      return buildResult(
          PaymentResult.from(payment),
          Optional.empty(),
          Money.zero(payment.amount().currency()),
          payment.effectiveAmount(),
          List.of());
    }

    ReceivableSettlement settlement = settlementLookup.orElseThrow();
    validateSettlement(payment, tenantId, settlement);
    try {
      Money allocatedAmount = settlement.allocatedAmount();
      Money unappliedAmount = settlement.unappliedAmount(payment);
      List<ReceivableAllocationResult> allocations =
          settlement.allocations().stream()
              .map(allocation -> ReceivableAllocationResult.from(settlement, allocation))
              .toList();
      return buildResult(
          PaymentResult.from(payment),
          Optional.of(settlement.id()),
          allocatedAmount,
          unappliedAmount,
          allocations);
    } catch (IllegalArgumentException | IllegalStateException exception) {
      throw corrupted(
          "Canonical Payment and receivable settlement state cannot form a valid read model",
          exception);
    }
  }

  private void authorize(GetReceivableSettlementQuery query, OperationsTenantId tenantId) {
    if (!authorizationPort.hasPermission(
        query.actor(), tenantId, OperationsPermission.READ_RECEIVABLE_SETTLEMENTS)) {
      throw new OperationsAccessDeniedException(
          tenantId, OperationsPermission.READ_RECEIVABLE_SETTLEMENTS);
    }
  }

  private static void validateSettlement(
      Payment payment, OperationsTenantId tenantId, ReceivableSettlement settlement) {
    if (!settlement.tenantId().equals(tenantId)) {
      throw corrupted("Tenant-scoped settlement lookup returned another tenant's settlement");
    }
    if (!settlement.paymentId().equals(payment.id())) {
      throw corrupted("Payment settlement lookup returned another Payment's settlement");
    }
    if (!settlement.customerId().equals(payment.customerId())) {
      throw corrupted("Payment settlement customer no longer matches the canonical Payment");
    }
    if (!settlement.currency().equals(payment.amount().currency())) {
      throw corrupted("Payment settlement currency no longer matches the canonical Payment");
    }
  }

  private static ReceivableSettlementResult buildResult(
      PaymentResult payment,
      Optional<ReceivableSettlementId> settlementId,
      Money allocatedAmount,
      Money unappliedAmount,
      List<ReceivableAllocationResult> allocations) {
    try {
      return new ReceivableSettlementResult(
          payment, settlementId, allocatedAmount, unappliedAmount, allocations);
    } catch (IllegalArgumentException exception) {
      throw corrupted("Receivable settlement read model is internally inconsistent", exception);
    }
  }

  private static ReceivableSettlementStateCorruptedException corrupted(String detail) {
    return new ReceivableSettlementStateCorruptedException(detail);
  }

  private static ReceivableSettlementStateCorruptedException corrupted(
      String detail, RuntimeException cause) {
    return new ReceivableSettlementStateCorruptedException(detail, cause);
  }
}
