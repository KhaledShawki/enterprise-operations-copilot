package io.github.khaledshawki.eoc.tenantaccess.application.service;

import io.github.khaledshawki.eoc.tenantaccess.application.exception.TenantMembershipNotFoundException;
import io.github.khaledshawki.eoc.tenantaccess.application.exception.TenantNotFoundException;
import io.github.khaledshawki.eoc.tenantaccess.application.port.in.GetTenantMembershipQuery;
import io.github.khaledshawki.eoc.tenantaccess.application.port.in.GetTenantMembershipResult;
import io.github.khaledshawki.eoc.tenantaccess.application.port.in.GetTenantMembershipUseCase;
import io.github.khaledshawki.eoc.tenantaccess.application.port.out.TenantMembershipRepository;
import io.github.khaledshawki.eoc.tenantaccess.application.port.out.TenantRepository;
import io.github.khaledshawki.eoc.tenantaccess.domain.model.TenantId;
import io.github.khaledshawki.eoc.tenantaccess.domain.model.TenantMembership;
import io.github.khaledshawki.eoc.tenantaccess.domain.model.TenantMembershipId;
import java.util.Objects;

public final class GetTenantMembershipService implements GetTenantMembershipUseCase {

  private final TenantRepository tenantRepository;
  private final TenantMembershipRepository tenantMembershipRepository;

  public GetTenantMembershipService(
      TenantRepository tenantRepository, TenantMembershipRepository tenantMembershipRepository) {
    this.tenantRepository =
        Objects.requireNonNull(tenantRepository, "Tenant repository cannot be null");
    this.tenantMembershipRepository =
        Objects.requireNonNull(
            tenantMembershipRepository, "Tenant membership repository cannot be null");
  }

  @Override
  public GetTenantMembershipResult get(GetTenantMembershipQuery query) {
    Objects.requireNonNull(query, "Query cannot be null");

    TenantId tenantId = TenantId.of(query.tenantId());
    TenantMembershipId membershipId = TenantMembershipId.of(query.membershipId());

    tenantRepository.findById(tenantId).orElseThrow(() -> new TenantNotFoundException(tenantId));

    TenantMembership membership =
        tenantMembershipRepository
            .findById(membershipId)
            .filter(candidate -> candidate.tenantId().equals(tenantId))
            .orElseThrow(() -> new TenantMembershipNotFoundException(tenantId, membershipId));

    return new GetTenantMembershipResult(
        membership.id(),
        membership.tenantId(),
        membership.userId(),
        membership.status(),
        membership.roles());
  }
}
