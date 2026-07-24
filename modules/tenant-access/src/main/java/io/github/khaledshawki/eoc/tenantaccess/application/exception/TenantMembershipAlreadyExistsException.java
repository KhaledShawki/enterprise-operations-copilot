package io.github.khaledshawki.eoc.tenantaccess.application.exception;

import io.github.khaledshawki.eoc.tenantaccess.domain.model.PlatformUserId;
import io.github.khaledshawki.eoc.tenantaccess.domain.model.TenantId;

public final class TenantMembershipAlreadyExistsException extends RuntimeException {

  public TenantMembershipAlreadyExistsException(TenantId tenantId, PlatformUserId userId) {
    super(message(tenantId, userId));
  }

  public TenantMembershipAlreadyExistsException(
      TenantId tenantId, PlatformUserId userId, Throwable cause) {
    super(message(tenantId, userId), cause);
  }

  private static String message(TenantId tenantId, PlatformUserId userId) {
    return "Membership already exists for tenant %s and platform user %s"
        .formatted(tenantId.value(), userId.value());
  }
}
