package io.github.khaledshawki.eoc.tenantaccess.application.exception;

import io.github.khaledshawki.eoc.tenantaccess.domain.model.TenantId;

public final class TenantNotActiveException extends RuntimeException {

  public TenantNotActiveException(TenantId tenantId) {
    super("Tenant " + tenantId.value() + " is not active");
  }
}
