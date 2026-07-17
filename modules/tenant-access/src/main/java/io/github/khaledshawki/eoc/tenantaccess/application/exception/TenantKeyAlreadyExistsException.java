package io.github.khaledshawki.eoc.tenantaccess.application.exception;

import io.github.khaledshawki.eoc.tenantaccess.domain.model.TenantKey;

public final class TenantKeyAlreadyExistsException extends RuntimeException {

  public TenantKeyAlreadyExistsException(TenantKey tenantKey) {
    super("Tenant key " + tenantKey.value() + " already exists");
  }

  public TenantKeyAlreadyExistsException(TenantKey tenantKey, Throwable cause) {
    super("Tenant key " + tenantKey.value() + " already exists", cause);
  }
}
