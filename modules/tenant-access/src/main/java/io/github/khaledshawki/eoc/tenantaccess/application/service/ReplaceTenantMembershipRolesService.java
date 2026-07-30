package io.github.khaledshawki.eoc.tenantaccess.application.service;

import io.github.khaledshawki.eoc.tenantaccess.application.exception.InvalidTenantRoleKeyException;
import io.github.khaledshawki.eoc.tenantaccess.application.exception.TenantMembershipNotFoundException;
import io.github.khaledshawki.eoc.tenantaccess.application.exception.TenantNotFoundException;
import io.github.khaledshawki.eoc.tenantaccess.application.port.in.ReplaceTenantMembershipRolesCommand;
import io.github.khaledshawki.eoc.tenantaccess.application.port.in.ReplaceTenantMembershipRolesResult;
import io.github.khaledshawki.eoc.tenantaccess.application.port.in.ReplaceTenantMembershipRolesUseCase;
import io.github.khaledshawki.eoc.tenantaccess.application.port.out.TenantMembershipRepository;
import io.github.khaledshawki.eoc.tenantaccess.application.port.out.TenantMembershipRoleWriteRepository;
import io.github.khaledshawki.eoc.tenantaccess.application.port.out.TenantRepository;
import io.github.khaledshawki.eoc.tenantaccess.domain.model.TenantId;
import io.github.khaledshawki.eoc.tenantaccess.domain.model.TenantMembership;
import io.github.khaledshawki.eoc.tenantaccess.domain.model.TenantMembershipId;
import io.github.khaledshawki.eoc.tenantaccess.domain.model.TenantRoleKey;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public final class ReplaceTenantMembershipRolesService
    implements ReplaceTenantMembershipRolesUseCase {

  private final TenantRepository tenantRepository;
  private final TenantMembershipRepository tenantMembershipRepository;
  private final TenantMembershipRoleWriteRepository tenantMembershipRoleWriteRepository;

  public ReplaceTenantMembershipRolesService(
      TenantRepository tenantRepository,
      TenantMembershipRepository tenantMembershipRepository,
      TenantMembershipRoleWriteRepository tenantMembershipRoleWriteRepository) {
    this.tenantRepository =
        Objects.requireNonNull(tenantRepository, "Tenant repository cannot be null");

    this.tenantMembershipRepository =
        Objects.requireNonNull(
            tenantMembershipRepository, "Tenant membership repository cannot be null");

    this.tenantMembershipRoleWriteRepository =
        Objects.requireNonNull(
            tenantMembershipRoleWriteRepository,
            "Tenant membership role write repository cannot be null");
  }

  @Override
  public ReplaceTenantMembershipRolesResult replaceRoles(
      ReplaceTenantMembershipRolesCommand command) {
    Objects.requireNonNull(command, "Command cannot be null");

    TenantId tenantId = TenantId.of(command.tenantId());
    TenantMembershipId membershipId = TenantMembershipId.of(command.membershipId());

    tenantRepository.findById(tenantId).orElseThrow(() -> new TenantNotFoundException(tenantId));

    TenantMembership membership =
        tenantMembershipRepository
            .findById(membershipId)
            .filter(candidate -> candidate.tenantId().equals(tenantId))
            .orElseThrow(() -> new TenantMembershipNotFoundException(tenantId, membershipId));

    Set<TenantRoleKey> normalizedRoles = normalizeRoles(command.roles());

    membership.replaceRoles(normalizedRoles);

    TenantMembership savedMembership = tenantMembershipRoleWriteRepository.replaceRoles(membership);

    return new ReplaceTenantMembershipRolesResult(
        savedMembership.id(),
        savedMembership.tenantId(),
        savedMembership.userId(),
        savedMembership.status(),
        savedMembership.roles());
  }

  private static Set<TenantRoleKey> normalizeRoles(Set<String> roles) {
    try {
      return roles.stream()
          .map(TenantRoleKey::of)
          .collect(Collectors.toCollection(LinkedHashSet::new));
    } catch (IllegalArgumentException exception) {
      throw new InvalidTenantRoleKeyException(exception);
    }
  }
}
