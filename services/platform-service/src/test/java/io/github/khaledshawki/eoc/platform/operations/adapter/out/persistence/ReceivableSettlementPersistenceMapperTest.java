package io.github.khaledshawki.eoc.platform.operations.adapter.out.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ReceivableSettlementPersistenceMapperTest {

  private static final Instant NOW = Instant.parse("2026-08-09T00:00:00Z");
  private static final CurrencyCode EUR = CurrencyCode.of("EUR");
  private static final OperationsTenantId TENANT_ID = OperationsTenantId.of(UUID.randomUUID());
  private static final BusinessPartnerId CUSTOMER_ID = BusinessPartnerId.of(UUID.randomUUID());
  private static final PaymentId PAYMENT_ID = PaymentId.of(UUID.randomUUID());
  private static final InvoiceId INVOICE_ID = InvoiceId.of(UUID.randomUUID());

  private final ReceivableSettlementPersistenceMapper mapper =
      new ReceivableSettlementPersistenceMapper();

  @Test
  void shouldRoundTripAllocationHistoryInStablePositionOrder() {
    ReceivableAllocation first =
        new ReceivableAllocation(
            ReceivableAllocationId.of(UUID.randomUUID()),
            INVOICE_ID,
            Money.of("20.00", EUR),
            ReceivableAllocationState.REVERSED);
    ReceivableAllocation second =
        new ReceivableAllocation(
            ReceivableAllocationId.of(UUID.randomUUID()),
            InvoiceId.of(UUID.randomUUID()),
            Money.of("30.00", EUR),
            ReceivableAllocationState.ACTIVE);
    ReceivableSettlement settlement = settlement(List.of(first, second));
    ReceivableSettlementJpaEntity settlementEntity = mapper.toEntity(settlement, NOW);
    List<ReceivableAllocationJpaEntity> allocationEntities =
        List.of(
            mapper.toEntity(settlement, first, 0, NOW),
            mapper.toEntity(settlement, second, 1, NOW));

    ReceivableSettlement restored = mapper.toDomain(settlementEntity, allocationEntities);

    assertEquals(settlement.id(), restored.id());
    assertEquals(settlement.tenantId(), restored.tenantId());
    assertEquals(settlement.customerId(), restored.customerId());
    assertEquals(settlement.paymentId(), restored.paymentId());
    assertEquals(settlement.currency(), restored.currency());
    assertEquals(settlement.allocations(), restored.allocations());
  }

  @Test
  void shouldRejectChangedSettlementImmutableStateOnUpdate() {
    ReceivableSettlement settlement = settlement(List.of());
    ReceivableSettlementJpaEntity entity =
        new ReceivableSettlementJpaEntity(
            settlement.id().value(),
            settlement.tenantId().value(),
            UUID.randomUUID(),
            settlement.paymentId().value(),
            settlement.currency().value(),
            NOW,
            NOW);

    assertThrows(
        ReceivableSettlementStateCorruptedException.class,
        () -> mapper.updateEntity(settlement, entity, NOW.plusSeconds(1)));
  }

  @Test
  void shouldRejectChangedAllocationImmutableStateOnUpdate() {
    ReceivableAllocation allocation =
        new ReceivableAllocation(
            ReceivableAllocationId.of(UUID.randomUUID()),
            INVOICE_ID,
            Money.of("20.00", EUR),
            ReceivableAllocationState.ACTIVE);
    ReceivableSettlement settlement = settlement(List.of(allocation));
    ReceivableAllocationJpaEntity entity =
        new ReceivableAllocationJpaEntity(
            allocation.id().value(),
            settlement.tenantId().value(),
            settlement.id().value(),
            allocation.invoiceId().value(),
            allocation.amount().currency().value(),
            Money.of("21.00", EUR).amount(),
            allocation.state().name(),
            0,
            NOW,
            NOW);

    assertThrows(
        ReceivableSettlementStateCorruptedException.class,
        () -> mapper.updateEntity(settlement, allocation, 0, entity, NOW.plusSeconds(1)));
  }

  @Test
  void shouldRejectNonContiguousPersistedPositions() {
    ReceivableSettlement settlement = settlement(List.of());
    ReceivableSettlementJpaEntity settlementEntity = mapper.toEntity(settlement, NOW);
    ReceivableAllocationJpaEntity allocationEntity =
        allocationEntity(settlement, "EUR", "ACTIVE", 1, settlement.tenantId().value());

    assertThrows(
        ReceivableSettlementStateCorruptedException.class,
        () -> mapper.toDomain(settlementEntity, List.of(allocationEntity)));
  }

  @Test
  void shouldRejectPersistedAllocationFromAnotherTenant() {
    ReceivableSettlement settlement = settlement(List.of());
    ReceivableSettlementJpaEntity settlementEntity = mapper.toEntity(settlement, NOW);
    ReceivableAllocationJpaEntity allocationEntity =
        allocationEntity(settlement, "EUR", "ACTIVE", 0, UUID.randomUUID());

    assertThrows(
        ReceivableSettlementStateCorruptedException.class,
        () -> mapper.toDomain(settlementEntity, List.of(allocationEntity)));
  }

  @Test
  void shouldRejectPersistedAllocationWithAnotherCurrency() {
    ReceivableSettlement settlement = settlement(List.of());
    ReceivableSettlementJpaEntity settlementEntity = mapper.toEntity(settlement, NOW);
    ReceivableAllocationJpaEntity allocationEntity =
        allocationEntity(settlement, "USD", "ACTIVE", 0, settlement.tenantId().value());

    assertThrows(
        ReceivableSettlementStateCorruptedException.class,
        () -> mapper.toDomain(settlementEntity, List.of(allocationEntity)));
  }

  @Test
  void shouldRejectPersistedAllocationWithUnsupportedState() {
    ReceivableSettlement settlement = settlement(List.of());
    ReceivableSettlementJpaEntity settlementEntity = mapper.toEntity(settlement, NOW);
    ReceivableAllocationJpaEntity allocationEntity =
        allocationEntity(settlement, "EUR", "UNKNOWN", 0, settlement.tenantId().value());

    assertThrows(
        ReceivableSettlementStateCorruptedException.class,
        () -> mapper.toDomain(settlementEntity, List.of(allocationEntity)));
  }

  private static ReceivableSettlement settlement(List<ReceivableAllocation> allocations) {
    return ReceivableSettlement.reconstitute(
        ReceivableSettlementId.of(UUID.randomUUID()),
        TENANT_ID,
        CUSTOMER_ID,
        PAYMENT_ID,
        EUR,
        allocations);
  }

  private static ReceivableAllocationJpaEntity allocationEntity(
      ReceivableSettlement settlement, String currency, String state, int position, UUID tenantId) {
    return new ReceivableAllocationJpaEntity(
        UUID.randomUUID(),
        tenantId,
        settlement.id().value(),
        INVOICE_ID.value(),
        currency,
        Money.of("10.00", CurrencyCode.of(currency)).amount(),
        state,
        position,
        NOW,
        NOW);
  }
}
