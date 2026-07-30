package io.github.khaledshawki.eoc.tenantaccess.domain.model;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

public final class TenantMembership {

  private final TenantMembershipId id;
  private final TenantId tenantId;
  private final PlatformUserId userId;
  private TenantMembershipStatus status;
  private Set<TenantRoleKey> roles;

  private TenantMembership(
      TenantMembershipId id,
      TenantId tenantId,
      PlatformUserId userId,
      TenantMembershipStatus status,
      Set<TenantRoleKey> roles) {
    this.id = Objects.requireNonNull(id, "Tenant membership id cannot be null");
    this.tenantId = Objects.requireNonNull(tenantId, "Tenant id cannot be null");
    this.userId = Objects.requireNonNull(userId, "Platform user id cannot be null");
    this.status = Objects.requireNonNull(status, "Tenant membership status cannot be null");
    this.roles = validatedRoleCopy(roles);
  }

  public static TenantMembership create(TenantId tenantId, PlatformUserId userId) {
    return new TenantMembership(
        TenantMembershipId.generate(), tenantId, userId, TenantMembershipStatus.ACTIVE, Set.of());
  }

  public static TenantMembership reconstitute(
      TenantMembershipId id,
      TenantId tenantId,
      PlatformUserId userId,
      TenantMembershipStatus status) {
    return reconstitute(id, tenantId, userId, status, Set.of());
  }

  public static TenantMembership reconstitute(
      TenantMembershipId id,
      TenantId tenantId,
      PlatformUserId userId,
      TenantMembershipStatus status,
      Set<TenantRoleKey> roles) {
    return new TenantMembership(id, tenantId, userId, status, roles);
  }

  public void suspend() {
    if (status == TenantMembershipStatus.SUSPENDED) {
      throw new IllegalStateException("Tenant membership is already suspended");
    }

    status = TenantMembershipStatus.SUSPENDED;
  }

  public void activate() {
    if (status == TenantMembershipStatus.ACTIVE) {
      throw new IllegalStateException("Tenant membership is already active");
    }

    status = TenantMembershipStatus.ACTIVE;
  }

  public void replaceRoles(Set<TenantRoleKey> roles) {
    this.roles = validatedRoleCopy(roles);
  }

  public TenantMembershipId id() {
    return id;
  }

  public TenantId tenantId() {
    return tenantId;
  }

  public PlatformUserId userId() {
    return userId;
  }

  public TenantMembershipStatus status() {
    return status;
  }

  public Set<TenantRoleKey> roles() {
    return roles;
  }

  private static Set<TenantRoleKey> validatedRoleCopy(Set<TenantRoleKey> roles) {
    Objects.requireNonNull(roles, "Tenant membership roles cannot be null");

    if (roles.stream().anyMatch(Objects::isNull)) {
      throw new IllegalArgumentException("Tenant membership roles cannot contain null values");
    }

    return Collections.unmodifiableSet(new LinkedHashSet<>(roles));
  }
}
