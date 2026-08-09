package io.github.khaledshawki.eoc.platform.operations.adapter.out.persistence;

import io.github.khaledshawki.eoc.operations.application.exception.ReceivableSettlementStateCorruptedException;
import io.github.khaledshawki.eoc.operations.domain.model.BusinessPartnerId;
import io.github.khaledshawki.eoc.operations.domain.model.CurrencyCode;
import io.github.khaledshawki.eoc.operations.domain.model.InvoiceId;
import io.github.khaledshawki.eoc.operations.domain.model.Money;
import io.github.khaledshawki.eoc.operations.domain.model.OperationsTenantId;
import io.github.khaledshawki.eoc.operations.domain.model.PaymentId;
import io.github.khaledshawki.eoc.operations.domain.model.ReceivableAllocation;
import io.github.khaledshawki.eoc.operations.domain.model.ReceivableAllocationId;
import io.github.khaledshawki.eoc.operations.domain.model.ReceivableAllocationState;
import io.github.khaledshawki.eoc.operations.domain.model.ReceivableSettlement;
import io.github.khaledshawki.eoc.operations.domain.model.ReceivableSettlementId;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

final class ReceivableSettlementPersistenceMapper {

  ReceivableSettlementJpaEntity toEntity(ReceivableSettlement settlement, Instant now) {
    Objects.requireNonNull(settlement, "Receivable settlement cannot be null");
    Objects.requireNonNull(now, "Timestamp cannot be null");
    return new ReceivableSettlementJpaEntity(
        settlement.id().value(),
        settlement.tenantId().value(),
        settlement.customerId().value(),
        settlement.paymentId().value(),
        settlement.currency().value(),
        now,
        now);
  }

  ReceivableSettlementJpaEntity updateEntity(
      ReceivableSettlement settlement, ReceivableSettlementJpaEntity entity, Instant now) {
    Objects.requireNonNull(settlement, "Receivable settlement cannot be null");
    Objects.requireNonNull(entity, "Receivable settlement entity cannot be null");
    Objects.requireNonNull(now, "Timestamp cannot be null");
    requireSettlementIdentityMatches(settlement, entity);
    entity.touch(now);
    return entity;
  }

  ReceivableAllocationJpaEntity toEntity(
      ReceivableSettlement settlement,
      ReceivableAllocation allocation,
      int allocationPosition,
      Instant now) {
    Objects.requireNonNull(settlement, "Receivable settlement cannot be null");
    Objects.requireNonNull(allocation, "Receivable allocation cannot be null");
    Objects.requireNonNull(now, "Timestamp cannot be null");
    return new ReceivableAllocationJpaEntity(
        allocation.id().value(),
        settlement.tenantId().value(),
        settlement.id().value(),
        allocation.invoiceId().value(),
        allocation.amount().currency().value(),
        allocation.amount().amount(),
        allocation.state().name(),
        allocationPosition,
        now,
        now);
  }

  ReceivableAllocationJpaEntity updateEntity(
      ReceivableSettlement settlement,
      ReceivableAllocation allocation,
      int allocationPosition,
      ReceivableAllocationJpaEntity entity,
      Instant now) {
    Objects.requireNonNull(settlement, "Receivable settlement cannot be null");
    Objects.requireNonNull(allocation, "Receivable allocation cannot be null");
    Objects.requireNonNull(entity, "Receivable allocation entity cannot be null");
    Objects.requireNonNull(now, "Timestamp cannot be null");
    requireAllocationIdentityMatches(settlement, allocation, allocationPosition, entity);
    String requestedState = allocation.state().name();
    if (!requestedState.equals(entity.getState())) {
      entity.updateState(requestedState, now);
    }
    return entity;
  }

  ReceivableSettlement toDomain(
      ReceivableSettlementJpaEntity settlementEntity,
      List<ReceivableAllocationJpaEntity> allocationEntities) {
    Objects.requireNonNull(settlementEntity, "Receivable settlement entity cannot be null");
    Objects.requireNonNull(allocationEntities, "Receivable allocation entities cannot be null");
    try {
      CurrencyCode settlementCurrency = CurrencyCode.of(settlementEntity.getCurrencyCode());
      List<ReceivableAllocation> allocations =
          toDomainAllocations(settlementEntity, settlementCurrency, allocationEntities);
      return ReceivableSettlement.reconstitute(
          ReceivableSettlementId.of(settlementEntity.getId()),
          OperationsTenantId.of(settlementEntity.getTenantId()),
          BusinessPartnerId.of(settlementEntity.getCustomerId()),
          PaymentId.of(settlementEntity.getPaymentId()),
          settlementCurrency,
          allocations);
    } catch (ReceivableSettlementStateCorruptedException exception) {
      throw exception;
    } catch (RuntimeException exception) {
      throw corrupted("Persisted receivable settlement cannot be reconstituted");
    }
  }

  private static List<ReceivableAllocation> toDomainAllocations(
      ReceivableSettlementJpaEntity settlementEntity,
      CurrencyCode settlementCurrency,
      List<ReceivableAllocationJpaEntity> allocationEntities) {
    ArrayList<ReceivableAllocation> allocations = new ArrayList<>(allocationEntities.size());
    for (int expectedPosition = 0;
        expectedPosition < allocationEntities.size();
        expectedPosition++) {
      ReceivableAllocationJpaEntity entity =
          Objects.requireNonNull(
              allocationEntities.get(expectedPosition),
              "Receivable allocation entity cannot be null");
      if (entity.getAllocationPosition() != expectedPosition) {
        throw corrupted("Persisted receivable allocation positions are not contiguous");
      }
      if (!entity.getTenantId().equals(settlementEntity.getTenantId())) {
        throw corrupted("Persisted receivable allocation tenant does not match its settlement");
      }
      if (!entity.getSettlementId().equals(settlementEntity.getId())) {
        throw corrupted("Persisted receivable allocation points to another settlement");
      }
      CurrencyCode allocationCurrency = CurrencyCode.of(entity.getCurrencyCode());
      if (!allocationCurrency.equals(settlementCurrency)) {
        throw corrupted("Persisted receivable allocation currency does not match its settlement");
      }
      ReceivableAllocationState state;
      try {
        state = ReceivableAllocationState.valueOf(entity.getState());
      } catch (IllegalArgumentException exception) {
        throw corrupted("Persisted receivable allocation has an unsupported state");
      }
      allocations.add(
          new ReceivableAllocation(
              ReceivableAllocationId.of(entity.getId()),
              InvoiceId.of(entity.getInvoiceId()),
              new Money(entity.getAmount(), allocationCurrency),
              state));
    }
    return allocations;
  }

  private static void requireSettlementIdentityMatches(
      ReceivableSettlement settlement, ReceivableSettlementJpaEntity entity) {
    if (!settlement.id().value().equals(entity.getId())
        || !settlement.tenantId().value().equals(entity.getTenantId())
        || !settlement.customerId().value().equals(entity.getCustomerId())
        || !settlement.paymentId().value().equals(entity.getPaymentId())
        || !settlement.currency().value().equals(entity.getCurrencyCode())) {
      throw corrupted(
          "Receivable settlement immutable persistence state does not match the aggregate");
    }
  }

  private static void requireAllocationIdentityMatches(
      ReceivableSettlement settlement,
      ReceivableAllocation allocation,
      int allocationPosition,
      ReceivableAllocationJpaEntity entity) {
    if (!allocation.id().value().equals(entity.getId())
        || !settlement.tenantId().value().equals(entity.getTenantId())
        || !settlement.id().value().equals(entity.getSettlementId())
        || !allocation.invoiceId().value().equals(entity.getInvoiceId())
        || !allocation.amount().currency().value().equals(entity.getCurrencyCode())
        || allocation.amount().amount().compareTo(entity.getAmount()) != 0
        || allocationPosition != entity.getAllocationPosition()) {
      throw corrupted(
          "Receivable allocation immutable persistence state does not match the aggregate");
    }
  }

  private static ReceivableSettlementStateCorruptedException corrupted(String detail) {
    return new ReceivableSettlementStateCorruptedException(detail);
  }
}
