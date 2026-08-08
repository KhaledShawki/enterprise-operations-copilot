package io.github.khaledshawki.eoc.platform.operations.adapter.out.persistence;

import io.github.khaledshawki.eoc.operations.domain.model.BusinessPartnerId;
import io.github.khaledshawki.eoc.operations.domain.model.CurrencyCode;
import io.github.khaledshawki.eoc.operations.domain.model.Money;
import io.github.khaledshawki.eoc.operations.domain.model.OperationsTenantId;
import io.github.khaledshawki.eoc.operations.domain.model.Payment;
import io.github.khaledshawki.eoc.operations.domain.model.PaymentId;
import java.time.Instant;
import java.util.Objects;

final class PaymentPersistenceMapper {

  PaymentJpaEntity toEntity(Payment payment, Instant now) {
    Objects.requireNonNull(payment, "Payment cannot be null");
    Objects.requireNonNull(now, "Timestamp cannot be null");
    return new PaymentJpaEntity(
        payment.id().value(),
        payment.tenantId().value(),
        payment.customerId().value(),
        payment.amount().currency().value(),
        payment.amount().amount(),
        payment.paymentDate(),
        payment.reversed(),
        now,
        now);
  }

  Payment toDomain(PaymentJpaEntity entity) {
    Objects.requireNonNull(entity, "Payment entity cannot be null");
    CurrencyCode currency = CurrencyCode.of(entity.getCurrencyCode());
    return Payment.reconstitute(
        PaymentId.of(entity.getId()),
        OperationsTenantId.of(entity.getTenantId()),
        BusinessPartnerId.of(entity.getCustomerId()),
        new Money(entity.getAmount(), currency),
        entity.getPaymentDate(),
        entity.isReversed());
  }

  PaymentJpaEntity updateEntity(Payment payment, PaymentJpaEntity entity, Instant now) {
    Objects.requireNonNull(payment, "Payment cannot be null");
    Objects.requireNonNull(entity, "Payment entity cannot be null");
    Objects.requireNonNull(now, "Timestamp cannot be null");
    ensureImmutableStateMatches(payment, entity);
    entity.updateMutableState(
        payment.customerId().value(),
        payment.amount().currency().value(),
        payment.amount().amount(),
        payment.paymentDate(),
        payment.reversed(),
        now);
    return entity;
  }

  private static void ensureImmutableStateMatches(Payment payment, PaymentJpaEntity entity) {
    if (!payment.id().value().equals(entity.getId())) {
      throw new IllegalArgumentException("Payment id mismatch");
    }
    if (!payment.tenantId().value().equals(entity.getTenantId())) {
      throw new IllegalArgumentException("Payment tenant id mismatch");
    }
  }
}
