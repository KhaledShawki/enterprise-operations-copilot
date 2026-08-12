package io.github.khaledshawki.eoc.copilot.application.model;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record CopilotReceivablesSummary(
    UUID tenantId,
    LocalDate businessDate,
    long invoiceCount,
    long openCount,
    long overdueCount,
    List<CopilotReceivableCurrencySummary> currencies) {
  public CopilotReceivablesSummary {
    Objects.requireNonNull(tenantId, "Copilot summary tenant id cannot be null");
    Objects.requireNonNull(businessDate, "Copilot summary business date cannot be null");
    if (invoiceCount < 0
        || openCount < 0
        || overdueCount < 0
        || openCount > invoiceCount
        || overdueCount > openCount) {
      throw new IllegalArgumentException("Copilot summary counts are inconsistent");
    }
    Objects.requireNonNull(currencies, "Copilot summary currencies cannot be null");
    if (currencies.stream().anyMatch(Objects::isNull)) {
      throw new NullPointerException("Copilot summary currencies cannot contain null values");
    }
    currencies = List.copyOf(currencies);
    for (int index = 1; index < currencies.size(); index++) {
      if (currencies.get(index - 1).currency().compareTo(currencies.get(index).currency()) >= 0) {
        throw new IllegalArgumentException("Copilot summary currencies must be unique and ordered");
      }
    }
    long currencyInvoiceCount = 0;
    long currencyOpenCount = 0;
    long currencyOverdueCount = 0;
    for (CopilotReceivableCurrencySummary currency : currencies) {
      currencyInvoiceCount = Math.addExact(currencyInvoiceCount, currency.invoiceCount());
      currencyOpenCount = Math.addExact(currencyOpenCount, currency.openCount());
      currencyOverdueCount = Math.addExact(currencyOverdueCount, currency.overdueCount());
    }
    if (invoiceCount != currencyInvoiceCount
        || openCount != currencyOpenCount
        || overdueCount != currencyOverdueCount) {
      throw new IllegalArgumentException("Copilot summary totals must equal currency totals");
    }
  }
}
