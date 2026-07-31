package io.github.khaledshawki.eoc.tenantaccess.application.service;

import io.github.khaledshawki.eoc.tenantaccess.application.port.in.ResolveTenantAccessQuery;
import io.github.khaledshawki.eoc.tenantaccess.application.port.in.ResolveTenantAccessResult;
import io.github.khaledshawki.eoc.tenantaccess.application.port.in.ResolveTenantAccessUseCase;
import io.github.khaledshawki.eoc.tenantaccess.application.port.out.PlatformUserRepository;
import io.github.khaledshawki.eoc.tenantaccess.application.port.out.TenantMembershipRepository;
import io.github.khaledshawki.eoc.tenantaccess.application.port.out.TenantRepository;
import io.github.khaledshawki.eoc.tenantaccess.domain.model.ExternalIdentity;
import io.github.khaledshawki.eoc.tenantaccess.domain.model.PlatformUser;
import io.github.khaledshawki.eoc.tenantaccess.domain.model.PlatformUserStatus;
import io.github.khaledshawki.eoc.tenantaccess.domain.model.Tenant;
import io.github.khaledshawki.eoc.tenantaccess.domain.model.TenantId;
import io.github.khaledshawki.eoc.tenantaccess.domain.model.TenantMembership;
import io.github.khaledshawki.eoc.tenantaccess.domain.model.TenantMembershipStatus;
import io.github.khaledshawki.eoc.tenantaccess.domain.model.TenantRoleKey;
import io.github.khaledshawki.eoc.tenantaccess.domain.model.TenantStatus;
import java.util.Objects;
import java.util.Optional;

public final class ResolveTenantAccessService implements ResolveTenantAccessUseCase {

  private final PlatformUserRepository platformUserRepository;
  private final TenantRepository tenantRepository;
  private final TenantMembershipRepository tenantMembershipRepository;

  public ResolveTenantAccessService(
      PlatformUserRepository platformUserRepository,
      TenantRepository tenantRepository,
      TenantMembershipRepository tenantMembershipRepository) {
    this.platformUserRepository =
        Objects.requireNonNull(platformUserRepository, "Platform user repository cannot be null");
    this.tenantRepository =
        Objects.requireNonNull(tenantRepository, "Tenant repository cannot be null");
    this.tenantMembershipRepository =
        Objects.requireNonNull(
            tenantMembershipRepository, "Tenant membership repository cannot be null");
  }

  @Override
  public ResolveTenantAccessResult resolve(ResolveTenantAccessQuery query) {
    Objects.requireNonNull(query, "Query cannot be null");

    ExternalIdentity externalIdentity = ExternalIdentity.of(query.issuer(), query.subject());
    TenantId tenantId = TenantId.of(query.tenantId());
    TenantRoleKey requiredRole = TenantRoleKey.of(query.requiredRole());

    Optional<PlatformUser> platformUser =
        platformUserRepository.findByExternalIdentity(externalIdentity);

    if (platformUser.isEmpty() || platformUser.get().status() != PlatformUserStatus.ACTIVE) {
      return ResolveTenantAccessResult.deny();
    }

    Optional<Tenant> tenant = tenantRepository.findById(tenantId);

    if (tenant.isEmpty() || tenant.get().status() != TenantStatus.ACTIVE) {
      return ResolveTenantAccessResult.deny();
    }

    PlatformUser activePlatformUser = platformUser.get();
    Optional<TenantMembership> membership =
        tenantMembershipRepository.findByTenantIdAndUserId(tenantId, activePlatformUser.id());

    if (membership.isEmpty()) {
      return ResolveTenantAccessResult.deny();
    }

    TenantMembership activeMembership = membership.get();

    if (!activeMembership.tenantId().equals(tenantId)
        || !activeMembership.userId().equals(activePlatformUser.id())
        || activeMembership.status() != TenantMembershipStatus.ACTIVE
        || !activeMembership.roles().contains(requiredRole)) {
      return ResolveTenantAccessResult.deny();
    }

    return ResolveTenantAccessResult.allow();
  }
}
