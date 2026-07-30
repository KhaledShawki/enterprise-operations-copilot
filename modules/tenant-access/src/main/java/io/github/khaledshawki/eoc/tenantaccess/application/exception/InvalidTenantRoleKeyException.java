package io.github.khaledshawki.eoc.tenantaccess.application.exception;

import java.util.Objects;

public final class InvalidTenantRoleKeyException extends IllegalArgumentException {

  public InvalidTenantRoleKeyException(IllegalArgumentException cause) {
    super(Objects.requireNonNull(cause, "Cause cannot be null").getMessage(), cause);
  }
}
