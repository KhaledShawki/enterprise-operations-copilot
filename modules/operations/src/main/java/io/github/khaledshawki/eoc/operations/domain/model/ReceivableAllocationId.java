package io.github.khaledshawki.eoc.operations.domain.model;

import java.util.Objects;
import java.util.UUID;

public record ReceivableAllocationId(UUID value) {

  public ReceivableAllocationId {
    Objects.requireNonNull(value, "Receivable allocation id cannot be null");
  }

  public static ReceivableAllocationId generate() {
    return new ReceivableAllocationId(UUID.randomUUID());
  }

  public static ReceivableAllocationId of(UUID value) {
    return new ReceivableAllocationId(value);
  }
}
