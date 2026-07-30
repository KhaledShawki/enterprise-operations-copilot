package io.github.khaledshawki.eoc.tenantaccess.application.service;

import io.github.khaledshawki.eoc.tenantaccess.application.exception.PlatformUserNotActiveException;
import io.github.khaledshawki.eoc.tenantaccess.application.exception.PlatformUserNotFoundException;
import io.github.khaledshawki.eoc.tenantaccess.application.exception.TenantMembershipAlreadyExistsException;
import io.github.khaledshawki.eoc.tenantaccess.application.exception.TenantNotActiveException;
import io.github.khaledshawki.eoc.tenantaccess.application.exception.TenantNotFoundException;
import io.github.khaledshawki.eoc.tenantaccess.application.port.in.AssignTenantMembershipCommand;
import io.github.khaledshawki.eoc.tenantaccess.application.port.in.AssignTenantMembershipResult;
import io.github.khaledshawki.eoc.tenantaccess.application.port.in.AssignTenantMembershipUseCase;
import io.github.khaledshawki.eoc.tenantaccess.application.port.out.PlatformUserRepository;
import io.github.khaledshawki.eoc.tenantaccess.application.port.out.TenantMembershipRepository;
import io.github.khaledshawki.eoc.tenantaccess.application.port.out.TenantRepository;
import io.github.khaledshawki.eoc.tenantaccess.domain.model.PlatformUser;
import io.github.khaledshawki.eoc.tenantaccess.domain.model.PlatformUserId;
import io.github.khaledshawki.eoc.tenantaccess.domain.model.PlatformUserStatus;
import io.github.khaledshawki.eoc.tenantaccess.domain.model.Tenant;
import io.github.khaledshawki.eoc.tenantaccess.domain.model.TenantId;
import io.github.khaledshawki.eoc.tenantaccess.domain.model.TenantMembership;
import io.github.khaledshawki.eoc.tenantaccess.domain.model.TenantStatus;
import java.util.Objects;

public final class AssignTenantMembershipService implements AssignTenantMembershipUseCase {

  private final TenantRepository tenantRepository;
  private final PlatformUserRepository platformUserRepository;
  private final TenantMembershipRepository tenantMembershipRepository;

  public AssignTenantMembershipService(
      TenantRepository tenantRepository,
      PlatformUserRepository platformUserRepository,
      TenantMembershipRepository tenantMembershipRepository) {
    this.tenantRepository =
        Objects.requireNonNull(tenantRepository, "Tenant repository cannot be null");
    this.platformUserRepository =
        Objects.requireNonNull(platformUserRepository, "Platform user repository cannot be null");
    this.tenantMembershipRepository =
        Objects.requireNonNull(
            tenantMembershipRepository, "Tenant membership repository cannot be null");
  }

  @Override
  public AssignTenantMembershipResult assign(AssignTenantMembershipCommand command) {
    Objects.requireNonNull(command, "Command cannot be null");

    TenantId tenantId = TenantId.of(command.tenantId());
    PlatformUserId platformUserId = PlatformUserId.of(command.platformUserId());

    Tenant tenant =
        tenantRepository
            .findById(tenantId)
            .orElseThrow(() -> new TenantNotFoundException(tenantId));

    PlatformUser platformUser =
        platformUserRepository
            .findById(platformUserId)
            .orElseThrow(() -> new PlatformUserNotFoundException(platformUserId));

    if (tenant.status() != TenantStatus.ACTIVE) {
      throw new TenantNotActiveException(tenantId);
    }

    if (platformUser.status() != PlatformUserStatus.ACTIVE) {
      throw new PlatformUserNotActiveException(platformUserId);
    }

    if (tenantMembershipRepository.existsByTenantIdAndUserId(tenantId, platformUserId)) {
      throw new TenantMembershipAlreadyExistsException(tenantId, platformUserId);
    }

    TenantMembership membership =
        tenantMembershipRepository.save(TenantMembership.create(tenantId, platformUserId));

    return new AssignTenantMembershipResult(
        membership.id(),
        membership.tenantId(),
        membership.userId(),
        membership.status(),
        membership.roles());
  }
}
