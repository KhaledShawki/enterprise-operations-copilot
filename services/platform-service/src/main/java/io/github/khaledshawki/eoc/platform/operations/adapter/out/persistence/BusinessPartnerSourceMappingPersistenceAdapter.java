package io.github.khaledshawki.eoc.platform.operations.adapter.out.persistence;

import io.github.khaledshawki.eoc.operations.application.exception.ConcurrentBusinessPartnerImportException;
import io.github.khaledshawki.eoc.operations.application.port.out.BusinessPartnerSourceMappingRepository;
import io.github.khaledshawki.eoc.operations.domain.model.BusinessPartnerSourceMapping;
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
class BusinessPartnerSourceMappingPersistenceAdapter
    implements BusinessPartnerSourceMappingRepository {

  private final SpringDataBusinessPartnerSourceMappingRepository sourceMappingRepository;
  private final BusinessPartnerSourceMappingPersistenceMapper persistenceMapper;
  private final Clock clock;

  BusinessPartnerSourceMappingPersistenceAdapter(
      SpringDataBusinessPartnerSourceMappingRepository sourceMappingRepository,
      BusinessPartnerSourceMappingPersistenceMapper persistenceMapper,
      Clock clock) {
    this.sourceMappingRepository =
        Objects.requireNonNull(
            sourceMappingRepository, "Business partner source mapping repository cannot be null");
    this.persistenceMapper =
        Objects.requireNonNull(
            persistenceMapper, "Business partner source mapping mapper cannot be null");
    this.clock = Objects.requireNonNull(clock, "Clock cannot be null");
  }

  @Override
  @Transactional
  public BusinessPartnerSourceMapping save(BusinessPartnerSourceMapping sourceMapping) {
    Objects.requireNonNull(sourceMapping, "Business partner source mapping cannot be null");
    Instant now = clock.instant();
    BusinessPartnerSourceMappingJpaId id = idOf(sourceMapping);
    BusinessPartnerSourceMappingJpaEntity entity =
        sourceMappingRepository
            .findById(id)
            .map(existing -> persistenceMapper.updateEntity(sourceMapping, existing, now))
            .orElseGet(() -> persistenceMapper.toEntity(sourceMapping, now));
    try {
      return persistenceMapper.toDomain(sourceMappingRepository.saveAndFlush(entity));
    } catch (DataIntegrityViolationException | ObjectOptimisticLockingFailureException exception) {
      throw new ConcurrentBusinessPartnerImportException(
          "Business partner source mapping was created or modified concurrently", exception);
    }
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<BusinessPartnerSourceMapping> findBySourceIdentity(
      OperationsTenantId tenantId,
      SourceSystemId sourceSystemId,
      SourceRecordIdentity sourceIdentity) {
    Objects.requireNonNull(tenantId, "Source mapping tenant id cannot be null");
    Objects.requireNonNull(sourceSystemId, "Source mapping system id cannot be null");
    Objects.requireNonNull(sourceIdentity, "Source mapping identity cannot be null");
    BusinessPartnerSourceMappingJpaId id =
        new BusinessPartnerSourceMappingJpaId(
            tenantId.value(),
            sourceSystemId.value(),
            sourceIdentity.kind(),
            sourceIdentity.value());
    return sourceMappingRepository.findById(id).map(persistenceMapper::toDomain);
  }

  private static BusinessPartnerSourceMappingJpaId idOf(
      BusinessPartnerSourceMapping sourceMapping) {
    return new BusinessPartnerSourceMappingJpaId(
        sourceMapping.tenantId().value(),
        sourceMapping.sourceSystemId().value(),
        sourceMapping.sourceIdentity().kind(),
        sourceMapping.sourceIdentity().value());
  }
}
