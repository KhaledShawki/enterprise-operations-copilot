package io.github.khaledshawki.eoc.platform.operations.adapter.out.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.khaledshawki.eoc.operations.domain.model.BusinessPartnerId;
import io.github.khaledshawki.eoc.operations.domain.model.CurrencyCode;
import io.github.khaledshawki.eoc.operations.domain.model.InvoiceId;
import io.github.khaledshawki.eoc.operations.domain.model.Money;
import io.github.khaledshawki.eoc.operations.domain.model.OperationsTenantId;
import io.github.khaledshawki.eoc.operations.domain.model.PaymentId;
import io.github.khaledshawki.eoc.operations.domain.model.ReceivableAllocation;
import io.github.khaledshawki.eoc.operations.domain.model.ReceivableAllocationId;
import io.github.khaledshawki.eoc.operations.domain.model.ReceivableSettlement;
import io.github.khaledshawki.eoc.operations.domain.model.ReceivableSettlementId;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ReceivableSettlementPersistenceAuditHistoryTest {

  private static final Instant FIRST_TIME = Instant.parse("2026-08-09T00:00:00Z");
  private static final Instant SECOND_TIME = Instant.parse("2026-08-09T01:00:00Z");
  private static final CurrencyCode EUR = CurrencyCode.of("EUR");

  @Test
  void shouldNotRewriteAuditTimestampWhenHistoricalAllocationStateIsUnchanged() {
    Fixture fixture = fixture();
    ReceivableSettlementPersistenceMapper mapper = new ReceivableSettlementPersistenceMapper();
    ReceivableAllocationJpaEntity entity =
        mapper.toEntity(fixture.settlement(), fixture.allocation(), 0, FIRST_TIME);

    mapper.updateEntity(fixture.settlement(), fixture.allocation(), 0, entity, SECOND_TIME);

    assertEquals(FIRST_TIME, entity.getUpdatedAt());
  }

  @Test
  void shouldUpdateAuditTimestampWhenAllocationStateActuallyChanges() {
    Fixture fixture = fixture();
    ReceivableSettlementPersistenceMapper mapper = new ReceivableSettlementPersistenceMapper();
    ReceivableAllocationJpaEntity entity =
        mapper.toEntity(fixture.settlement(), fixture.allocation(), 0, FIRST_TIME);
    ReceivableAllocation reversed = fixture.allocation().reverse();
    ReceivableSettlement reversedSettlement =
        ReceivableSettlement.reconstitute(
            fixture.settlement().id(),
            fixture.settlement().tenantId(),
            fixture.settlement().customerId(),
            fixture.settlement().paymentId(),
            fixture.settlement().currency(),
            List.of(reversed));

    mapper.updateEntity(reversedSettlement, reversed, 0, entity, SECOND_TIME);

    assertEquals("REVERSED", entity.getState());
    assertEquals(SECOND_TIME, entity.getUpdatedAt());
  }

  private static Fixture fixture() {
    ReceivableAllocation allocation =
        ReceivableAllocation.active(
            ReceivableAllocationId.of(UUID.fromString("00000000-0000-0000-0000-000000000501")),
            InvoiceId.of(UUID.fromString("00000000-0000-0000-0000-000000000401")),
            Money.of("10.00", EUR));
    ReceivableSettlement settlement =
        ReceivableSettlement.reconstitute(
            ReceivableSettlementId.of(UUID.fromString("00000000-0000-0000-0000-000000000601")),
            OperationsTenantId.of(UUID.fromString("00000000-0000-0000-0000-000000000101")),
            BusinessPartnerId.of(UUID.fromString("00000000-0000-0000-0000-000000000201")),
            PaymentId.of(UUID.fromString("00000000-0000-0000-0000-000000000301")),
            EUR,
            List.of(allocation));
    return new Fixture(settlement, allocation);
  }

  private record Fixture(ReceivableSettlement settlement, ReceivableAllocation allocation) {}
}
