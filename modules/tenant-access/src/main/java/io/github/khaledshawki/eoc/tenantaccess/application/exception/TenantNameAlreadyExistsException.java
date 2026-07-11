package io.github.khaledshawki.eoc.tenantaccess.application.exception;

import io.github.khaledshawki.eoc.tenantaccess.domain.model.TenantName;

public final class TenantNameAlreadyExistsException extends RuntimeException {

  public TenantNameAlreadyExistsException(TenantName tenantName) {
    super("Tenant name " + tenantName.value() + " already exists");
  }
}
