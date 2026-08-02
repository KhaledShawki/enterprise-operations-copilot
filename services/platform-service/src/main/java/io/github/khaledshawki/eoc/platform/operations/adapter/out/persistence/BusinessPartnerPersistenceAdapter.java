package io.github.khaledshawki.eoc.platform.operations.adapter.out.persistence;

import io.github.khaledshawki.eoc.operations.application.exception.ConcurrentBusinessPartnerImportException;
import io.github.khaledshawki.eoc.operations.application.port.out.BusinessPartnerRepository;
import io.github.khaledshawki.eoc.operations.domain.model.BusinessPartner;
import io.github.khaledshawki.eoc.operations.domain.model.BusinessPartnerId;
import io.github.khaledshawki.eoc.operations.domain.model.OperationsTenantId;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
class BusinessPartnerPersistenceAdapter implements BusinessPartnerRepository {

  private final SpringDataBusinessPartnerRepository businessPartnerRepository;
  private final BusinessPartnerPersistenceMapper persistenceMapper;
  private final Clock clock;

  BusinessPartnerPersistenceAdapter(
      SpringDataBusinessPartnerRepository businessPartnerRepository,
      BusinessPartnerPersistenceMapper persistenceMapper,
      Clock clock) {
    this.businessPartnerRepository =
        Objects.requireNonNull(
            businessPartnerRepository, "Business partner JPA repository cannot be null");
    this.persistenceMapper =
        Objects.requireNonNull(persistenceMapper, "Business partner mapper cannot be null");
    this.clock = Objects.requireNonNull(clock, "Clock cannot be null");
  }

  @Override
  @Transactional
  public BusinessPartner save(BusinessPartner businessPartner) {
    Objects.requireNonNull(businessPartner, "Business partner cannot be null");
    Instant now = clock.instant();
    BusinessPartnerJpaEntity entity =
        businessPartnerRepository
            .findByIdAndTenantId(businessPartner.id().value(), businessPartner.tenantId().value())
            .map(existing -> persistenceMapper.updateEntity(businessPartner, existing, now))
            .orElseGet(() -> persistenceMapper.toEntity(businessPartner, now));
    try {
      return persistenceMapper.toDomain(businessPartnerRepository.saveAndFlush(entity));
    } catch (ObjectOptimisticLockingFailureException exception) {
      throw concurrentModification(businessPartner.id(), exception);
    }
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<BusinessPartner> findById(
      OperationsTenantId tenantId, BusinessPartnerId businessPartnerId) {
    Objects.requireNonNull(tenantId, "Business partner tenant id cannot be null");
    Objects.requireNonNull(businessPartnerId, "Business partner id cannot be null");
    return businessPartnerRepository
        .findByIdAndTenantId(businessPartnerId.value(), tenantId.value())
        .map(persistenceMapper::toDomain);
  }

  private static ConcurrentBusinessPartnerImportException concurrentModification(
      BusinessPartnerId businessPartnerId, RuntimeException cause) {
    return new ConcurrentBusinessPartnerImportException(
        "Business partner " + businessPartnerId.value() + " was modified concurrently", cause);
  }
}
