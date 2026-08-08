package io.github.khaledshawki.eoc.operations.application.port.in;

import io.github.khaledshawki.eoc.operations.application.model.authorization.OperationsActor;
import java.util.Objects;
import java.util.UUID;

public record GetPaymentQuery(OperationsActor actor, UUID tenantId, UUID paymentId) {

  public GetPaymentQuery {
    Objects.requireNonNull(actor, "Operations actor cannot be null");
    Objects.requireNonNull(tenantId, "Payment tenant id cannot be null");
    Objects.requireNonNull(paymentId, "Payment id cannot be null");
  }
}
