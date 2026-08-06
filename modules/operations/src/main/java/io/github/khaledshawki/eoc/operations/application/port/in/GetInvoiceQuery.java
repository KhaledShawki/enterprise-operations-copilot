package io.github.khaledshawki.eoc.operations.application.port.in;

import io.github.khaledshawki.eoc.operations.application.model.authorization.OperationsActor;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

public record GetInvoiceQuery(
    OperationsActor actor, UUID tenantId, UUID invoiceId, LocalDate businessDate) {

  public GetInvoiceQuery {
    Objects.requireNonNull(actor, "Operations actor cannot be null");
    Objects.requireNonNull(tenantId, "Invoice tenant id cannot be null");
    Objects.requireNonNull(invoiceId, "Invoice id cannot be null");
    Objects.requireNonNull(businessDate, "Invoice business date cannot be null");
  }
}
