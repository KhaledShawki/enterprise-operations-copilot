package io.github.khaledshawki.eoc.tenantaccess.application.exception;

import io.github.khaledshawki.eoc.tenantaccess.domain.model.TenantId;

public final class TenantNotFoundException extends RuntimeException {

  public TenantNotFoundException(TenantId tenantId) {
    super("Tenant " + tenantId.value() + " was not found");
  }
}
