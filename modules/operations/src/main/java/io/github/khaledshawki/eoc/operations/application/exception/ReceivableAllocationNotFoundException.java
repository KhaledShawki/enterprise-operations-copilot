package io.github.khaledshawki.eoc.operations.application.exception;

import io.github.khaledshawki.eoc.operations.domain.model.OperationsTenantId;
import io.github.khaledshawki.eoc.operations.domain.model.ReceivableAllocationId;
import java.util.Objects;

public final class ReceivableAllocationNotFoundException extends RuntimeException {

  public ReceivableAllocationNotFoundException(
      OperationsTenantId tenantId, ReceivableAllocationId allocationId) {
    super(
        "Receivable allocation "
            + Objects.requireNonNull(allocationId, "Receivable allocation id cannot be null")
                .value()
            + " was not found for tenant "
            + Objects.requireNonNull(tenantId, "Operations tenant id cannot be null").value());
  }
}
