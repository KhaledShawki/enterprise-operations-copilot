package io.github.khaledshawki.eoc.operations.application.exception;

import io.github.khaledshawki.eoc.operations.domain.model.InvoiceId;
import io.github.khaledshawki.eoc.operations.domain.model.OperationsTenantId;
import java.util.Objects;

public final class InvoiceNotFoundException extends RuntimeException {

  public InvoiceNotFoundException(OperationsTenantId tenantId, InvoiceId invoiceId) {
    super(
        "Invoice "
            + Objects.requireNonNull(invoiceId, "Invoice id cannot be null").value()
            + " was not found for tenant "
            + Objects.requireNonNull(tenantId, "Invoice tenant id cannot be null").value());
  }
}
