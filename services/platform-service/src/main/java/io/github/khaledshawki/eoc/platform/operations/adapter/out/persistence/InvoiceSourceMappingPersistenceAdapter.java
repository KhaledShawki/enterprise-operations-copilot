package io.github.khaledshawki.eoc.platform.operations.adapter.out.persistence;

import io.github.khaledshawki.eoc.operations.application.exception.ConcurrentInvoiceImportException;
import io.github.khaledshawki.eoc.operations.application.port.out.InvoiceSourceMappingRepository;
import io.github.khaledshawki.eoc.operations.domain.model.InvoiceSourceMapping;
import io.github.khaledshawki.eoc.operations.domain.model.OperationsTenantId;
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
class InvoiceSourceMappingPersistenceAdapter implements InvoiceSourceMappingRepository {

  private final SpringDataInvoiceSourceMappingRepository sourceMappingRepository;
  private final InvoiceSourceMappingPersistenceMapper persistenceMapper;
  private final Clock clock;

  InvoiceSourceMappingPersistenceAdapter(
      SpringDataInvoiceSourceMappingRepository sourceMappingRepository,
      InvoiceSourceMappingPersistenceMapper persistenceMapper,
      Clock clock) {
    this.sourceMappingRepository =
        Objects.requireNonNull(
            sourceMappingRepository, "Invoice source mapping repository cannot be null");
    this.persistenceMapper =
        Objects.requireNonNull(persistenceMapper, "Invoice source mapping mapper cannot be null");
    this.clock = Objects.requireNonNull(clock, "Clock cannot be null");
  }

  @Override
  @Transactional
  public InvoiceSourceMapping save(InvoiceSourceMapping sourceMapping) {
    Objects.requireNonNull(sourceMapping, "Invoice source mapping cannot be null");
    Instant now = clock.instant();
    InvoiceSourceMappingJpaId id = idOf(sourceMapping);
    InvoiceSourceMappingJpaEntity entity =
        sourceMappingRepository
            .findById(id)
            .map(existing -> persistenceMapper.updateEntity(sourceMapping, existing, now))
            .orElseGet(() -> persistenceMapper.toEntity(sourceMapping, now));
    try {
      return persistenceMapper.toDomain(sourceMappingRepository.saveAndFlush(entity));
    } catch (DataIntegrityViolationException | ObjectOptimisticLockingFailureException exception) {
      throw new ConcurrentInvoiceImportException(
          "Invoice source mapping was created or modified concurrently", exception);
    }
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<InvoiceSourceMapping> findBySourceIdentity(
      OperationsTenantId tenantId,
      SourceSystemId sourceSystemId,
      SourceRecordIdentity sourceIdentity) {
    Objects.requireNonNull(tenantId, "Invoice source mapping tenant id cannot be null");
    Objects.requireNonNull(sourceSystemId, "Invoice source mapping system id cannot be null");
    Objects.requireNonNull(sourceIdentity, "Invoice source mapping identity cannot be null");
    return sourceMappingRepository
        .findById(
            new InvoiceSourceMappingJpaId(
                tenantId.value(),
                sourceSystemId.value(),
                sourceIdentity.kind(),
                sourceIdentity.value()))
        .map(persistenceMapper::toDomain);
  }

  private static InvoiceSourceMappingJpaId idOf(InvoiceSourceMapping sourceMapping) {
    return new InvoiceSourceMappingJpaId(
        sourceMapping.tenantId().value(),
        sourceMapping.sourceSystemId().value(),
        sourceMapping.sourceIdentity().kind(),
        sourceMapping.sourceIdentity().value());
  }
}
