package io.github.khaledshawki.eoc.platform.operations.adapter.out.persistence;

import io.github.khaledshawki.eoc.operations.application.exception.ConcurrentPaymentImportException;
import io.github.khaledshawki.eoc.operations.application.port.out.PaymentSourceMappingRepository;
import io.github.khaledshawki.eoc.operations.domain.model.OperationsTenantId;
import io.github.khaledshawki.eoc.operations.domain.model.PaymentSourceMapping;
import io.github.khaledshawki.eoc.operations.domain.model.SourceRecordIdentity;
import io.github.khaledshawki.eoc.operations.domain.model.SourceSystemId;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
class PaymentSourceMappingPersistenceAdapter implements PaymentSourceMappingRepository {

  private final SpringDataPaymentSourceMappingRepository sourceMappingRepository;
  private final PaymentSourceMappingPersistenceMapper persistenceMapper;
  private final Clock clock;

  PaymentSourceMappingPersistenceAdapter(
      SpringDataPaymentSourceMappingRepository sourceMappingRepository,
      PaymentSourceMappingPersistenceMapper persistenceMapper,
      Clock clock) {
    this.sourceMappingRepository =
        Objects.requireNonNull(
            sourceMappingRepository, "Payment source mapping repository cannot be null");
    this.persistenceMapper =
        Objects.requireNonNull(persistenceMapper, "Payment source mapping mapper cannot be null");
    this.clock = Objects.requireNonNull(clock, "Clock cannot be null");
  }

  @Override
  @Transactional
  public PaymentSourceMapping save(PaymentSourceMapping sourceMapping) {
    Objects.requireNonNull(sourceMapping, "Payment source mapping cannot be null");
    Instant now = clock.instant();
    PaymentSourceMappingJpaId id = idOf(sourceMapping);
    PaymentSourceMappingJpaEntity entity =
        sourceMappingRepository
            .findById(id)
            .map(existing -> persistenceMapper.updateEntity(sourceMapping, existing, now))
            .orElseGet(() -> persistenceMapper.toEntity(sourceMapping, now));
    try {
      return persistenceMapper.toDomain(sourceMappingRepository.saveAndFlush(entity));
    } catch (DataIntegrityViolationException | ObjectOptimisticLockingFailureException exception) {
      throw new ConcurrentPaymentImportException(
          "Payment source mapping was created or modified concurrently", exception);
    }
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<PaymentSourceMapping> findBySourceIdentity(
      OperationsTenantId tenantId,
      SourceSystemId sourceSystemId,
      SourceRecordIdentity sourceIdentity) {
    Objects.requireNonNull(tenantId, "Payment source mapping tenant id cannot be null");
    Objects.requireNonNull(sourceSystemId, "Payment source mapping system id cannot be null");
    Objects.requireNonNull(sourceIdentity, "Payment source mapping identity cannot be null");
    return sourceMappingRepository
        .findById(
            new PaymentSourceMappingJpaId(
                tenantId.value(),
                sourceSystemId.value(),
                sourceIdentity.kind(),
                sourceIdentity.value()))
        .map(persistenceMapper::toDomain);
  }

  private static PaymentSourceMappingJpaId idOf(PaymentSourceMapping sourceMapping) {
    return new PaymentSourceMappingJpaId(
        sourceMapping.tenantId().value(),
        sourceMapping.sourceSystemId().value(),
        sourceMapping.sourceIdentity().kind(),
        sourceMapping.sourceIdentity().value());
  }
}
