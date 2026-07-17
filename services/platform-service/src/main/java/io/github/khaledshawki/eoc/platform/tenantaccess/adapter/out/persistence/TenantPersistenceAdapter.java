package io.github.khaledshawki.eoc.platform.tenantaccess.adapter.out.persistence;

import io.github.khaledshawki.eoc.tenantaccess.application.exception.TenantKeyAlreadyExistsException;
import io.github.khaledshawki.eoc.tenantaccess.application.port.out.TenantRepository;
import io.github.khaledshawki.eoc.tenantaccess.domain.model.Tenant;
import io.github.khaledshawki.eoc.tenantaccess.domain.model.TenantId;
import io.github.khaledshawki.eoc.tenantaccess.domain.model.TenantKey;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
class TenantPersistenceAdapter implements TenantRepository {
  private static final String TENANT_KEY_UNIQUE_CONSTRAINT = "uk_tenants_tenant_key";

  private final SpringDataTenantRepository tenantRepository;
  private final TenantPersistenceMapper tenantPersistenceMapper;
  private final Clock clock;

  TenantPersistenceAdapter(
      SpringDataTenantRepository tenantRepository,
      TenantPersistenceMapper tenantPersistenceMapper,
      Clock clock) {
    this.tenantRepository =
        Objects.requireNonNull(tenantRepository, "Tenant repository cannot be null");
    this.tenantPersistenceMapper =
        Objects.requireNonNull(tenantPersistenceMapper, "Tenant persistence mapper cannot be null");
    this.clock = Objects.requireNonNull(clock, "Clock cannot be null");
  }

  @Override
  @Transactional
  public Tenant save(Tenant tenant) {
    Objects.requireNonNull(tenant, "Tenant cannot be null");

    Instant now = clock.instant();

    TenantJpaEntity entity =
        tenantRepository
            .findById(tenant.id().value())
            .map(existing -> tenantPersistenceMapper.updateEntity(tenant, existing, now))
            .orElseGet(() -> tenantPersistenceMapper.toEntity(tenant, now));
    try {
      TenantJpaEntity savedTenant = tenantRepository.saveAndFlush(entity);
      return tenantPersistenceMapper.toDomain(savedTenant);
    } catch (DataIntegrityViolationException exception) {
      if (isTenantKeyUniqueConstraintViolation(exception)) {
        throw new TenantKeyAlreadyExistsException(tenant.key(), exception);
      }
      throw exception;
    }
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<Tenant> findById(TenantId tenantId) {
    Objects.requireNonNull(tenantId, "Tenant id cannot be null");
    return tenantRepository.findById(tenantId.value()).map(tenantPersistenceMapper::toDomain);
  }

  @Override
  @Transactional(readOnly = true)
  public boolean existsByKey(TenantKey key) {
    Objects.requireNonNull(key, "Tenant key cannot be null");
    return tenantRepository.existsByTenantKey(key.value());
  }

  private static boolean isTenantKeyUniqueConstraintViolation(Throwable exception) {
    Throwable cause = exception;

    while (cause != null) {
      if (cause instanceof ConstraintViolationException constraintViolation
          && TENANT_KEY_UNIQUE_CONSTRAINT.equals(constraintViolation.getConstraintName())) {
        return true;
      }

      cause = cause.getCause();
    }

    return false;
  }
}
