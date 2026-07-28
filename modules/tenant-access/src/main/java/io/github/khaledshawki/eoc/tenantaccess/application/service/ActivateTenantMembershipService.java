package io.github.khaledshawki.eoc.tenantaccess.application.service;

import io.github.khaledshawki.eoc.tenantaccess.application.exception.TenantMembershipAlreadyActiveException;
import io.github.khaledshawki.eoc.tenantaccess.application.exception.TenantMembershipNotFoundException;
import io.github.khaledshawki.eoc.tenantaccess.application.exception.TenantNotFoundException;
import io.github.khaledshawki.eoc.tenantaccess.application.port.in.ActivateTenantMembershipCommand;
import io.github.khaledshawki.eoc.tenantaccess.application.port.in.ActivateTenantMembershipResult;
import io.github.khaledshawki.eoc.tenantaccess.application.port.in.ActivateTenantMembershipUseCase;
import io.github.khaledshawki.eoc.tenantaccess.application.port.out.TenantMembershipRepository;
import io.github.khaledshawki.eoc.tenantaccess.application.port.out.TenantRepository;
import io.github.khaledshawki.eoc.tenantaccess.domain.model.TenantId;
import io.github.khaledshawki.eoc.tenantaccess.domain.model.TenantMembership;
import io.github.khaledshawki.eoc.tenantaccess.domain.model.TenantMembershipId;
import io.github.khaledshawki.eoc.tenantaccess.domain.model.TenantMembershipStatus;
import java.util.Objects;

public final class ActivateTenantMembershipService implements ActivateTenantMembershipUseCase {

  private final TenantRepository tenantRepository;
  private final TenantMembershipRepository tenantMembershipRepository;

  public ActivateTenantMembershipService(
      TenantRepository tenantRepository, TenantMembershipRepository tenantMembershipRepository) {
    this.tenantRepository =
        Objects.requireNonNull(tenantRepository, "Tenant repository cannot be null");

    this.tenantMembershipRepository =
        Objects.requireNonNull(
            tenantMembershipRepository, "Tenant membership repository cannot be null");
  }

  @Override
  public ActivateTenantMembershipResult activate(ActivateTenantMembershipCommand command) {
    Objects.requireNonNull(command, "Command cannot be null");

    TenantId tenantId = TenantId.of(command.tenantId());

    TenantMembershipId membershipId = TenantMembershipId.of(command.membershipId());

    tenantRepository.findById(tenantId).orElseThrow(() -> new TenantNotFoundException(tenantId));

    TenantMembership membership =
        tenantMembershipRepository
            .findById(membershipId)
            .filter(candidate -> candidate.tenantId().equals(tenantId))
            .orElseThrow(() -> new TenantMembershipNotFoundException(tenantId, membershipId));

    if (membership.status() == TenantMembershipStatus.ACTIVE) {
      throw new TenantMembershipAlreadyActiveException(tenantId, membershipId);
    }

    membership.activate();

    TenantMembership savedMembership = tenantMembershipRepository.save(membership);

    return new ActivateTenantMembershipResult(
        savedMembership.id(),
        savedMembership.tenantId(),
        savedMembership.userId(),
        savedMembership.status());
  }
}
