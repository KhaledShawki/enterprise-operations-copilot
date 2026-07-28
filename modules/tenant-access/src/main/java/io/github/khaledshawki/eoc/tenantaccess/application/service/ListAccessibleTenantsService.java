package io.github.khaledshawki.eoc.tenantaccess.application.service;

import io.github.khaledshawki.eoc.tenantaccess.application.exception.PlatformUserNotActiveException;
import io.github.khaledshawki.eoc.tenantaccess.application.exception.PlatformUserNotFoundException;
import io.github.khaledshawki.eoc.tenantaccess.application.port.in.AccessibleTenantResult;
import io.github.khaledshawki.eoc.tenantaccess.application.port.in.ListAccessibleTenantsQuery;
import io.github.khaledshawki.eoc.tenantaccess.application.port.in.ListAccessibleTenantsResult;
import io.github.khaledshawki.eoc.tenantaccess.application.port.in.ListAccessibleTenantsUseCase;
import io.github.khaledshawki.eoc.tenantaccess.application.port.out.AccessibleTenantProjection;
import io.github.khaledshawki.eoc.tenantaccess.application.port.out.AccessibleTenantQueryRepository;
import io.github.khaledshawki.eoc.tenantaccess.application.port.out.PlatformUserRepository;
import io.github.khaledshawki.eoc.tenantaccess.domain.model.ExternalIdentity;
import io.github.khaledshawki.eoc.tenantaccess.domain.model.PlatformUser;
import io.github.khaledshawki.eoc.tenantaccess.domain.model.PlatformUserStatus;
import io.github.khaledshawki.eoc.tenantaccess.domain.model.TenantMembershipStatus;
import io.github.khaledshawki.eoc.tenantaccess.domain.model.TenantStatus;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public final class ListAccessibleTenantsService implements ListAccessibleTenantsUseCase {

  private final PlatformUserRepository platformUserRepository;

  private final AccessibleTenantQueryRepository accessibleTenantQueryRepository;

  public ListAccessibleTenantsService(
      PlatformUserRepository platformUserRepository,
      AccessibleTenantQueryRepository accessibleTenantQueryRepository) {
    this.platformUserRepository =
        Objects.requireNonNull(platformUserRepository, "Platform user repository cannot be null");

    this.accessibleTenantQueryRepository =
        Objects.requireNonNull(
            accessibleTenantQueryRepository, "Accessible tenant query repository cannot be null");
  }

  @Override
  public ListAccessibleTenantsResult list(ListAccessibleTenantsQuery query) {
    Objects.requireNonNull(query, "Query cannot be null");

    ExternalIdentity externalIdentity = ExternalIdentity.of(query.issuer(), query.subject());

    PlatformUser platformUser =
        platformUserRepository
            .findByExternalIdentity(externalIdentity)
            .orElseThrow(() -> new PlatformUserNotFoundException(externalIdentity));

    if (platformUser.status() != PlatformUserStatus.ACTIVE) {
      throw new PlatformUserNotActiveException(platformUser.id());
    }

    List<AccessibleTenantProjection> projections =
        Objects.requireNonNull(
            accessibleTenantQueryRepository.findAllByPlatformUserId(platformUser.id()),
            "Accessible tenant projections cannot be null");

    List<AccessibleTenantResult> accessibleTenants =
        projections.stream()
            .map(
                projection ->
                    Objects.requireNonNull(
                        projection, "Accessible tenant projection cannot be null"))
            .filter(
                projection ->
                    projection.tenantStatus() == TenantStatus.ACTIVE
                        && projection.membershipStatus() == TenantMembershipStatus.ACTIVE)
            .sorted(Comparator.comparing(projection -> projection.key().value()))
            .map(ListAccessibleTenantsService::toResult)
            .toList();

    return new ListAccessibleTenantsResult(accessibleTenants);
  }

  private static AccessibleTenantResult toResult(AccessibleTenantProjection projection) {
    return new AccessibleTenantResult(
        projection.membershipId(), projection.tenantId(), projection.key(), projection.name());
  }
}
