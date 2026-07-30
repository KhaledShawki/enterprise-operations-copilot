package io.github.khaledshawki.eoc.platform.tenantaccess.adapter.out.persistence;

import io.github.khaledshawki.eoc.tenantaccess.application.port.out.AccessibleTenantProjection;
import io.github.khaledshawki.eoc.tenantaccess.application.port.out.AccessibleTenantQueryRepository;
import io.github.khaledshawki.eoc.tenantaccess.domain.model.PlatformUserId;
import io.github.khaledshawki.eoc.tenantaccess.domain.model.TenantId;
import io.github.khaledshawki.eoc.tenantaccess.domain.model.TenantKey;
import io.github.khaledshawki.eoc.tenantaccess.domain.model.TenantMembershipId;
import io.github.khaledshawki.eoc.tenantaccess.domain.model.TenantName;
import io.github.khaledshawki.eoc.tenantaccess.domain.model.TenantRoleKey;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
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

    List<AccessibleTenantJpaProjection> rows =
        Objects.requireNonNull(
            membershipRepository.findAccessibleTenantsByPlatformUserId(platformUserId.value()),
            "Accessible tenant JPA projections cannot be null");

    Map<UUID, AccessibleTenantAccumulator> accumulators = new LinkedHashMap<>();

    for (AccessibleTenantJpaProjection row : rows) {
      Objects.requireNonNull(row, "Accessible tenant JPA projection cannot be null");

      AccessibleTenantAccumulator accumulator =
          accumulators.computeIfAbsent(
              row.getMembershipId(), ignored -> new AccessibleTenantAccumulator(row));

      accumulator.addRole(row.getRoleKey());
    }

    return accumulators.values().stream()
        .map(AccessibleTenantAccumulator::toApplicationProjection)
        .toList();
  }

  private static final class AccessibleTenantAccumulator {

    private final AccessibleTenantJpaProjection firstRow;

    private final Set<TenantRoleKey> roles = new LinkedHashSet<>();

    private AccessibleTenantAccumulator(AccessibleTenantJpaProjection firstRow) {
      this.firstRow =
          Objects.requireNonNull(firstRow, "Accessible tenant JPA projection cannot be null");
    }

    private void addRole(String roleKey) {
      if (roleKey != null) {
        roles.add(TenantRoleKey.of(roleKey));
      }
    }

    private AccessibleTenantProjection toApplicationProjection() {
      return new AccessibleTenantProjection(
          TenantMembershipId.of(firstRow.getMembershipId()),
          TenantId.of(firstRow.getTenantId()),
          TenantKey.of(firstRow.getTenantKey()),
          TenantName.of(firstRow.getDisplayName()),
          firstRow.getTenantStatus(),
          firstRow.getMembershipStatus(),
          Set.copyOf(roles));
    }
  }
}
