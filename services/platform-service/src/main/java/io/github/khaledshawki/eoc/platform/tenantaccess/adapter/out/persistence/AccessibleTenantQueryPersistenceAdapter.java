package io.github.khaledshawki.eoc.platform.tenantaccess.adapter.out.persistence;

import io.github.khaledshawki.eoc.tenantaccess.application.port.out.AccessibleTenantProjection;
import io.github.khaledshawki.eoc.tenantaccess.application.port.out.AccessibleTenantQueryRepository;
import io.github.khaledshawki.eoc.tenantaccess.domain.model.PlatformUserId;
import io.github.khaledshawki.eoc.tenantaccess.domain.model.TenantId;
import io.github.khaledshawki.eoc.tenantaccess.domain.model.TenantKey;
import io.github.khaledshawki.eoc.tenantaccess.domain.model.TenantMembershipId;
import io.github.khaledshawki.eoc.tenantaccess.domain.model.TenantName;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
class AccessibleTenantQueryPersistenceAdapter implements AccessibleTenantQueryRepository {

  private final SpringDataTenantMembershipRepository membershipRepository;

  AccessibleTenantQueryPersistenceAdapter(
      SpringDataTenantMembershipRepository membershipRepository) {
    this.membershipRepository =
        Objects.requireNonNull(membershipRepository, "Membership repository cannot be null");
  }

  @Override
  @Transactional(readOnly = true)
  public List<AccessibleTenantProjection> findAllByPlatformUserId(PlatformUserId platformUserId) {
    Objects.requireNonNull(platformUserId, "Platform user id cannot be null");

    return membershipRepository
        .findAccessibleTenantsByPlatformUserId(platformUserId.value())
        .stream()
        .map(AccessibleTenantQueryPersistenceAdapter::toApplicationProjection)
        .toList();
  }

  private static AccessibleTenantProjection toApplicationProjection(
      AccessibleTenantJpaProjection projection) {
    Objects.requireNonNull(projection, "Accessible tenant JPA projection cannot be null");

    return new AccessibleTenantProjection(
        TenantMembershipId.of(projection.getMembershipId()),
        TenantId.of(projection.getTenantId()),
        TenantKey.of(projection.getTenantKey()),
        TenantName.of(projection.getDisplayName()),
        projection.getTenantStatus(),
        projection.getMembershipStatus());
  }
}
