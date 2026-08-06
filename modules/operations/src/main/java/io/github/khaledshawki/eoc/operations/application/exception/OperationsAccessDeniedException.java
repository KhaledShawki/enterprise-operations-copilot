package io.github.khaledshawki.eoc.operations.application.exception;

import io.github.khaledshawki.eoc.operations.application.model.authorization.OperationsPermission;
import io.github.khaledshawki.eoc.operations.domain.model.OperationsTenantId;
import java.util.Objects;

public final class OperationsAccessDeniedException extends RuntimeException {

  private final OperationsTenantId tenantId;
  private final OperationsPermission permission;

  public OperationsAccessDeniedException(
      OperationsTenantId tenantId, OperationsPermission permission) {
    super(
        "Operations permission "
            + Objects.requireNonNull(permission, "Operations permission cannot be null")
            + " was denied for tenant "
            + Objects.requireNonNull(tenantId, "Operations tenant id cannot be null").value());
    this.tenantId = tenantId;
    this.permission = permission;
  }

  public OperationsTenantId tenantId() {
    return tenantId;
  }

  public OperationsPermission permission() {
    return permission;
  }
}
