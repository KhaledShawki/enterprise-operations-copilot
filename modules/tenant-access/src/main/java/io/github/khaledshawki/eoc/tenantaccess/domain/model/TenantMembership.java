package io.github.khaledshawki.eoc.tenantaccess.domain.model;

import java.util.Objects;

public final class TenantMembership {

  private final TenantMembershipId id;
  private final TenantId tenantId;
  private final PlatformUserId userId;
  private TenantMembershipStatus status;

  private TenantMembership(
      TenantMembershipId id,
      TenantId tenantId,
      PlatformUserId userId,
      TenantMembershipStatus status) {
    this.id = Objects.requireNonNull(id, "Tenant membership id cannot be null");
    this.tenantId = Objects.requireNonNull(tenantId, "Tenant id cannot be null");
    this.userId = Objects.requireNonNull(userId, "Platform user id cannot be null");
    this.status = Objects.requireNonNull(status, "Tenant membership status cannot be null");
  }

  public static TenantMembership create(TenantId tenantId, PlatformUserId userId) {
    return new TenantMembership(
        TenantMembershipId.generate(), tenantId, userId, TenantMembershipStatus.ACTIVE);
  }

  public static TenantMembership reconstitute(
      TenantMembershipId id,
      TenantId tenantId,
      PlatformUserId userId,
      TenantMembershipStatus status) {
    return new TenantMembership(id, tenantId, userId, status);
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
}
