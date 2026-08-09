package io.github.khaledshawki.eoc.operations.application.port.in;

import io.github.khaledshawki.eoc.operations.application.model.authorization.OperationsActor;
import java.util.Objects;
import java.util.UUID;

public record GetReceivableReconciliationQuery(
    OperationsActor actor, UUID tenantId, UUID invoiceId) {

  public GetReceivableReconciliationQuery {
    Objects.requireNonNull(actor, "Operations actor cannot be null");
    Objects.requireNonNull(tenantId, "Receivable reconciliation tenant id cannot be null");
    Objects.requireNonNull(invoiceId, "Receivable reconciliation Invoice id cannot be null");
  }
}
