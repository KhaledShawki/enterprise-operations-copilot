package io.github.khaledshawki.eoc.analytics.application.port.in;

import io.github.khaledshawki.eoc.analytics.application.model.querying.ReceivableCurrencySummary;
import io.github.khaledshawki.eoc.analytics.application.model.querying.ReceivableSummarySnapshot;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record ReceivablesSummaryResult(
    UUID tenantId,
    LocalDate businessDate,
    long invoiceCount,
    long openCount,
    long overdueCount,
    List<ReceivableCurrencySummary> currencies) {

  public ReceivablesSummaryResult {
    Objects.requireNonNull(tenantId, "Receivable summary result tenant id cannot be null");
    Objects.requireNonNull(businessDate, "Receivable summary result business date cannot be null");
    if (invoiceCount < 0 || openCount < 0 || overdueCount < 0) {
      throw new IllegalArgumentException("Receivable summary result counts cannot be negative");
    }
    if (openCount > invoiceCount || overdueCount > openCount) {
      throw new IllegalArgumentException("Receivable summary result counts are inconsistent");
    }
    Objects.requireNonNull(currencies, "Receivable summary result currencies cannot be null");
    if (currencies.stream().anyMatch(Objects::isNull)) {
      throw new NullPointerException("Receivable summary result currencies cannot contain null");
    }
    currencies = List.copyOf(currencies);
  }

  public static ReceivablesSummaryResult from(ReceivableSummarySnapshot snapshot) {
    Objects.requireNonNull(snapshot, "Receivable summary snapshot cannot be null");
    long invoiceCount = 0;
    long openCount = 0;
    long overdueCount = 0;
    for (ReceivableCurrencySummary currency : snapshot.currencies()) {
      invoiceCount = Math.addExact(invoiceCount, currency.invoiceCount());
      openCount = Math.addExact(openCount, currency.openCount());
      overdueCount = Math.addExact(overdueCount, currency.overdueCount());
    }
    return new ReceivablesSummaryResult(
        snapshot.tenantId().value(),
        snapshot.businessDate(),
        invoiceCount,
        openCount,
        overdueCount,
        snapshot.currencies());
  }
}
