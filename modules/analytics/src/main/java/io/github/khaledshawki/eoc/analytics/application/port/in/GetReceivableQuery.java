package io.github.khaledshawki.eoc.analytics.application.port.in;

import io.github.khaledshawki.eoc.analytics.domain.model.AnalyticsTenantId;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

public record GetReceivableQuery(UUID tenantId, UUID invoiceId, LocalDate businessDate) {

  public GetReceivableQuery {
    Objects.requireNonNull(tenantId, "Receivable tenant id cannot be null");
    Objects.requireNonNull(invoiceId, "Receivable invoice id cannot be null");
    Objects.requireNonNull(businessDate, "Receivable business date cannot be null");
  }

  public AnalyticsTenantId analyticsTenantId() {
    return AnalyticsTenantId.of(tenantId);
  }
}
