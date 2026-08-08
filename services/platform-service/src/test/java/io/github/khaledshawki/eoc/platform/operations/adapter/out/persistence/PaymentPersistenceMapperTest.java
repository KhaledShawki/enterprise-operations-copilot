package io.github.khaledshawki.eoc.platform.operations.adapter.out.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.khaledshawki.eoc.operations.domain.model.BusinessPartnerId;
import io.github.khaledshawki.eoc.operations.domain.model.CurrencyCode;
import io.github.khaledshawki.eoc.operations.domain.model.Money;
import io.github.khaledshawki.eoc.operations.domain.model.OperationsTenantId;
import io.github.khaledshawki.eoc.operations.domain.model.Payment;
import io.github.khaledshawki.eoc.operations.domain.model.PaymentId;
import io.github.khaledshawki.eoc.operations.domain.model.PaymentStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PaymentPersistenceMapperTest {

  private static final OperationsTenantId TENANT_ID =
      OperationsTenantId.of(UUID.fromString("00000000-0000-0000-0000-000000000001"));
  private static final BusinessPartnerId CUSTOMER_ID =
      BusinessPartnerId.of(UUID.fromString("00000000-0000-0000-0000-000000000002"));
  private static final BusinessPartnerId SECOND_CUSTOMER_ID =
      BusinessPartnerId.of(UUID.fromString("00000000-0000-0000-0000-000000000003"));
  private static final Instant NOW = Instant.parse("2026-08-08T08:00:00Z");
  private static final CurrencyCode EUR = CurrencyCode.of("EUR");
  private static final CurrencyCode USD = CurrencyCode.of("USD");

  private final PaymentPersistenceMapper mapper = new PaymentPersistenceMapper();

  @Test
  void shouldRoundTripPaymentFactsIdentityAndEffectiveAmount() {
    Payment payment =
        Payment.reconstitute(
            PaymentId.of(UUID.fromString("00000000-0000-0000-0000-000000000010")),
            TENANT_ID,
            CUSTOMER_ID,
            Money.of("120.00", EUR),
            LocalDate.parse("2026-08-08"),
            false);

    PaymentJpaEntity entity = mapper.toEntity(payment, NOW);
    Payment restored = mapper.toDomain(entity);

    assertEquals(payment.id(), restored.id());
    assertEquals(payment.tenantId(), restored.tenantId());
    assertEquals(payment.customerId(), restored.customerId());
    assertEquals(payment.amount(), restored.amount());
    assertEquals(payment.paymentDate(), restored.paymentDate());
    assertFalse(restored.reversed());
    assertEquals(PaymentStatus.RECORDED, restored.status());
    assertEquals(Money.of("120.00", EUR), restored.effectiveAmount());
    assertEquals(NOW, entity.getCreatedAt());
    assertEquals(NOW, entity.getUpdatedAt());
  }

  @Test
  void shouldUpdateMutableFactsIncludingReversalAndRejectIdentityMismatches() {
    Payment original =
        Payment.reconstitute(
            PaymentId.of(UUID.fromString("00000000-0000-0000-0000-000000000010")),
            TENANT_ID,
            CUSTOMER_ID,
            Money.of("120.00", EUR),
            LocalDate.parse("2026-08-08"),
            false);
    PaymentJpaEntity entity = mapper.toEntity(original, NOW);
    Instant later = NOW.plusSeconds(60);
    Payment updated =
        Payment.reconstitute(
            original.id(),
            TENANT_ID,
            SECOND_CUSTOMER_ID,
            Money.of("150.00", USD),
            LocalDate.parse("2026-08-09"),
            true);

    mapper.updateEntity(updated, entity, later);
    Payment restored = mapper.toDomain(entity);

    assertEquals(SECOND_CUSTOMER_ID.value(), entity.getCustomerId());
    assertEquals("USD", entity.getCurrencyCode());
    assertEquals(Money.of("150.00", USD).amount(), entity.getAmount());
    assertEquals(LocalDate.parse("2026-08-09"), entity.getPaymentDate());
    assertTrue(entity.isReversed());
    assertEquals(SECOND_CUSTOMER_ID, restored.customerId());
    assertEquals(Money.of("150.00", USD), restored.amount());
    assertTrue(restored.reversed());
    assertEquals(PaymentStatus.REVERSED, restored.status());
    assertEquals(Money.zero(USD), restored.effectiveAmount());
    assertEquals(later, entity.getUpdatedAt());
    assertThrows(
        IllegalArgumentException.class,
        () ->
            mapper.updateEntity(
                Payment.reconstitute(
                    PaymentId.generate(),
                    TENANT_ID,
                    updated.customerId(),
                    updated.amount(),
                    updated.paymentDate(),
                    updated.reversed()),
                entity,
                later));
  }
}
