package io.github.khaledshawki.eoc.platform.tenantaccess.adapter.out.persistence;

import io.github.khaledshawki.eoc.platform.persistence.PersistenceConstraintViolationDetector;
import io.github.khaledshawki.eoc.tenantaccess.application.exception.TenantMembershipAlreadyExistsException;
import io.github.khaledshawki.eoc.tenantaccess.application.port.out.TenantMembershipRepository;
import io.github.khaledshawki.eoc.tenantaccess.domain.model.PlatformUserId;
import io.github.khaledshawki.eoc.tenantaccess.domain.model.TenantId;
import io.github.khaledshawki.eoc.tenantaccess.domain.model.TenantMembership;
import io.github.khaledshawki.eoc.tenantaccess.domain.model.TenantMembershipId;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
class TenantMembershipPersistenceAdapter implements TenantMembershipRepository {

  private static final String TENANT_USER_UNIQUE_CONSTRAINT = "uk_tenant_memberships_tenant_user";

  private final SpringDataTenantMembershipRepository membershipRepository;
  private final TenantMembershipPersistenceMapper membershipMapper;
  private final Clock clock;

  TenantMembershipPersistenceAdapter(
      SpringDataTenantMembershipRepository membershipRepository,
      TenantMembershipPersistenceMapper membershipMapper,
      Clock clock) {
    this.membershipRepository =
        Objects.requireNonNull(membershipRepository, "Membership repository cannot be null");
    this.membershipMapper =
        Objects.requireNonNull(membershipMapper, "Membership mapper cannot be null");
    this.clock = Objects.requireNonNull(clock, "Clock cannot be null");
  }

  @Override
  @Transactional
  public TenantMembership save(TenantMembership membership) {
    Objects.requireNonNull(membership, "Tenant membership cannot be null");

    Instant now = clock.instant();

    TenantMembershipJpaEntity entity =
        membershipRepository
            .findById(membership.id().value())
            .map(existing -> membershipMapper.updateEntity(membership, existing, now))
            .orElseGet(() -> membershipMapper.toEntity(membership, now));

    try {
      return membershipMapper.toDomain(membershipRepository.saveAndFlush(entity));
    } catch (DataIntegrityViolationException exception) {
      if (PersistenceConstraintViolationDetector.hasConstraintName(
          exception, TENANT_USER_UNIQUE_CONSTRAINT)) {
        throw new TenantMembershipAlreadyExistsException(
            membership.tenantId(), membership.userId(), exception);
      }

      throw exception;
    }
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<TenantMembership> findById(TenantMembershipId membershipId) {
    Objects.requireNonNull(membershipId, "Membership id cannot be null");

    return membershipRepository.findById(membershipId.value()).map(membershipMapper::toDomain);
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<TenantMembership> findByTenantIdAndUserId(
      TenantId tenantId, PlatformUserId platformUserId) {
    Objects.requireNonNull(tenantId, "Tenant id cannot be null");
    Objects.requireNonNull(platformUserId, "Platform user id cannot be null");

    return membershipRepository
        .findByTenantIdAndPlatformUserId(tenantId.value(), platformUserId.value())
        .map(membershipMapper::toDomain);
  }

  @Override
  @Transactional(readOnly = true)
  public boolean existsByTenantIdAndUserId(TenantId tenantId, PlatformUserId platformUserId) {
    Objects.requireNonNull(tenantId, "Tenant id cannot be null");
    Objects.requireNonNull(platformUserId, "Platform user id cannot be null");

    return membershipRepository.existsByTenantIdAndPlatformUserId(
        tenantId.value(), platformUserId.value());
  }
}
