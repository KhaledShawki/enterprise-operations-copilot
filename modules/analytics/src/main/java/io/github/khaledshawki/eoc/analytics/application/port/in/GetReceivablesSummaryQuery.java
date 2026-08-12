package io.github.khaledshawki.eoc.analytics.application.port.in;

import io.github.khaledshawki.eoc.analytics.domain.model.AnalyticsTenantId;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

public record GetReceivablesSummaryQuery(UUID tenantId, LocalDate businessDate) {

  public GetReceivablesSummaryQuery {
    Objects.requireNonNull(tenantId, "Receivable summary tenant id cannot be null");
    Objects.requireNonNull(businessDate, "Receivable summary business date cannot be null");
  }

  public AnalyticsTenantId analyticsTenantId() {
    return AnalyticsTenantId.of(tenantId);
  }
}
