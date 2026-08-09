package io.github.khaledshawki.eoc.platform.operations.adapter.out.persistence;

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

public final class ReceivableAllocationJpaId implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  private UUID id;
  private UUID tenantId;

  public ReceivableAllocationJpaId() {}

  ReceivableAllocationJpaId(UUID id, UUID tenantId) {
    this.id = Objects.requireNonNull(id, "Receivable allocation id cannot be null");
    this.tenantId =
        Objects.requireNonNull(tenantId, "Receivable allocation tenant id cannot be null");
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }
    if (!(other instanceof ReceivableAllocationJpaId that)) {
      return false;
    }
    return Objects.equals(id, that.id) && Objects.equals(tenantId, that.tenantId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, tenantId);
  }
}
