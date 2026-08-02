package io.github.khaledshawki.eoc.operations.domain.model;

import java.util.Objects;
import java.util.UUID;

public record OperationsTenantId(UUID value) {

  public OperationsTenantId {
    Objects.requireNonNull(value, "Operations tenant id cannot be null");
  }

  public static OperationsTenantId of(UUID value) {
    return new OperationsTenantId(value);
  }
}
