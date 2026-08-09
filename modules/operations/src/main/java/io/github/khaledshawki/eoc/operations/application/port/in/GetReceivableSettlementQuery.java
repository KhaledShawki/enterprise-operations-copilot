package io.github.khaledshawki.eoc.operations.application.port.in;

import io.github.khaledshawki.eoc.operations.application.model.authorization.OperationsActor;
import java.util.Objects;
import java.util.UUID;

public record GetReceivableSettlementQuery(OperationsActor actor, UUID tenantId, UUID paymentId) {

  public GetReceivableSettlementQuery {
    Objects.requireNonNull(actor, "Operations actor cannot be null");
    Objects.requireNonNull(tenantId, "Receivable settlement tenant id cannot be null");
    Objects.requireNonNull(paymentId, "Receivable settlement payment id cannot be null");
  }
}
