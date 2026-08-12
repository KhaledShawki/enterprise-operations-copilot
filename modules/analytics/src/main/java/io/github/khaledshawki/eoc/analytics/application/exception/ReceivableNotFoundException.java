package io.github.khaledshawki.eoc.analytics.application.exception;

import io.github.khaledshawki.eoc.analytics.domain.model.AnalyticsTenantId;
import java.util.Objects;
import java.util.UUID;

public final class ReceivableNotFoundException extends RuntimeException {

  public ReceivableNotFoundException(AnalyticsTenantId tenantId, UUID invoiceId) {
    super(
        "Receivable projection not found for tenant %s and invoice %s"
            .formatted(
                Objects.requireNonNull(tenantId, "Analytics tenant id cannot be null").value(),
                Objects.requireNonNull(invoiceId, "Invoice id cannot be null")));
  }
}
