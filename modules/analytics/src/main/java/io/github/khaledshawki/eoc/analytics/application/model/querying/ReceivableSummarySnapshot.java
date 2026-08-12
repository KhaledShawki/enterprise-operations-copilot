package io.github.khaledshawki.eoc.analytics.application.model.querying;

import io.github.khaledshawki.eoc.analytics.domain.model.AnalyticsTenantId;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

public record ReceivableSummarySnapshot(
    AnalyticsTenantId tenantId,
    LocalDate businessDate,
    List<ReceivableCurrencySummary> currencies) {

  public ReceivableSummarySnapshot {
    Objects.requireNonNull(tenantId, "Receivable summary tenant id cannot be null");
    Objects.requireNonNull(businessDate, "Receivable summary business date cannot be null");
    Objects.requireNonNull(currencies, "Receivable summary currencies cannot be null");
    if (currencies.stream().anyMatch(Objects::isNull)) {
      throw new NullPointerException("Receivable summary currencies cannot contain null");
    }
    currencies = List.copyOf(currencies);
  }
}
