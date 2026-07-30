package io.github.khaledshawki.eoc.platform.tenantaccess.adapter.out.persistence;

import io.github.khaledshawki.eoc.tenantaccess.application.exception.TenantMembershipNotFoundException;
import io.github.khaledshawki.eoc.tenantaccess.application.port.out.TenantMembershipRoleWriteRepository;
import io.github.khaledshawki.eoc.tenantaccess.domain.model.TenantMembership;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
class TenantMembershipRoleWritePersistenceAdapter implements TenantMembershipRoleWriteRepository {

  private final SpringDataTenantMembershipRepository membershipRepository;
  private final TenantMembershipPersistenceMapper membershipMapper;
  private final Clock clock;

  TenantMembershipRoleWritePersistenceAdapter(
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
  public TenantMembership replaceRoles(TenantMembership membership) {
    Objects.requireNonNull(membership, "Tenant membership cannot be null");

    Instant now = clock.instant();

    TenantMembershipJpaEntity entity =
        membershipRepository
            .findById(membership.id().value())
            .orElseThrow(
                () ->
                    new TenantMembershipNotFoundException(membership.tenantId(), membership.id()));

    membershipMapper.replaceRoles(membership, entity, now);

    return membershipMapper.toDomain(membershipRepository.saveAndFlush(entity));
  }
}
