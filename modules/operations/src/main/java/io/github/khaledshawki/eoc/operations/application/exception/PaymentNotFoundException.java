package io.github.khaledshawki.eoc.operations.application.exception;

import io.github.khaledshawki.eoc.operations.domain.model.OperationsTenantId;
import io.github.khaledshawki.eoc.operations.domain.model.PaymentId;
import java.util.Objects;

public final class PaymentNotFoundException extends RuntimeException {

  public PaymentNotFoundException(OperationsTenantId tenantId, PaymentId paymentId) {
    super(
        "Payment "
            + Objects.requireNonNull(paymentId, "Payment id cannot be null").value()
            + " was not found for tenant "
            + Objects.requireNonNull(tenantId, "Payment tenant id cannot be null").value());
  }
}
