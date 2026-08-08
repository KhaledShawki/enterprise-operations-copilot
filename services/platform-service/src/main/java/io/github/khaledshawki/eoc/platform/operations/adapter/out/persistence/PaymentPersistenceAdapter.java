package io.github.khaledshawki.eoc.platform.operations.adapter.out.persistence;

import io.github.khaledshawki.eoc.operations.application.exception.ConcurrentPaymentImportException;
import io.github.khaledshawki.eoc.operations.application.port.out.PaymentRepository;
import io.github.khaledshawki.eoc.operations.domain.model.OperationsTenantId;
import io.github.khaledshawki.eoc.operations.domain.model.Payment;
import io.github.khaledshawki.eoc.operations.domain.model.PaymentId;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
class PaymentPersistenceAdapter implements PaymentRepository {

  private final SpringDataPaymentRepository paymentRepository;
  private final PaymentPersistenceMapper persistenceMapper;
  private final Clock clock;

  PaymentPersistenceAdapter(
      SpringDataPaymentRepository paymentRepository,
      PaymentPersistenceMapper persistenceMapper,
      Clock clock) {
    this.paymentRepository =
        Objects.requireNonNull(paymentRepository, "Payment JPA repository cannot be null");
    this.persistenceMapper =
        Objects.requireNonNull(persistenceMapper, "Payment mapper cannot be null");
    this.clock = Objects.requireNonNull(clock, "Clock cannot be null");
  }

  @Override
  @Transactional
  public Payment save(Payment payment) {
    Objects.requireNonNull(payment, "Payment cannot be null");
    Instant now = clock.instant();
    PaymentJpaEntity entity =
        paymentRepository
            .findByIdAndTenantId(payment.id().value(), payment.tenantId().value())
            .map(existing -> persistenceMapper.updateEntity(payment, existing, now))
            .orElseGet(() -> persistenceMapper.toEntity(payment, now));
    try {
      return persistenceMapper.toDomain(paymentRepository.saveAndFlush(entity));
    } catch (DataIntegrityViolationException | ObjectOptimisticLockingFailureException exception) {
      throw concurrentModification(payment.id(), exception);
    }
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<Payment> findById(OperationsTenantId tenantId, PaymentId paymentId) {
    Objects.requireNonNull(tenantId, "Payment tenant id cannot be null");
    Objects.requireNonNull(paymentId, "Payment id cannot be null");
    return paymentRepository
        .findByIdAndTenantId(paymentId.value(), tenantId.value())
        .map(persistenceMapper::toDomain);
  }

  private static ConcurrentPaymentImportException concurrentModification(
      PaymentId paymentId, RuntimeException cause) {
    return new ConcurrentPaymentImportException(
        "Payment " + paymentId.value() + " was created or modified concurrently", cause);
  }
}
