package io.github.khaledshawki.eoc.copilot.application.model;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

public record CopilotReceivableCurrencySummary(
    String currency,
    long invoiceCount,
    long openCount,
    long overdueCount,
    CopilotMoney outstandingAmount,
    CopilotMoney overdueAmount,
    CopilotMoney currentAmount,
    CopilotMoney days1To30OverdueAmount,
    CopilotMoney days31To60OverdueAmount,
    CopilotMoney days61To90OverdueAmount,
    CopilotMoney days91PlusOverdueAmount) {
  public CopilotReceivableCurrencySummary {
    Objects.requireNonNull(currency, "Copilot summary currency cannot be null");
    if (invoiceCount < 0
        || openCount < 0
        || overdueCount < 0
        || openCount > invoiceCount
        || overdueCount > openCount) {
      throw new IllegalArgumentException("Copilot summary counts are inconsistent");
    }
    List<CopilotMoney> amounts =
        List.of(
            outstandingAmount,
            overdueAmount,
            currentAmount,
            days1To30OverdueAmount,
            days31To60OverdueAmount,
            days61To90OverdueAmount,
            days91PlusOverdueAmount);
    if (amounts.stream().anyMatch(money -> !currency.equals(money.currency()))) {
      throw new IllegalArgumentException("Copilot summary currencies must match");
    }
    BigDecimal buckets =
        days1To30OverdueAmount
            .amount()
            .add(days31To60OverdueAmount.amount())
            .add(days61To90OverdueAmount.amount())
            .add(days91PlusOverdueAmount.amount());
    if (overdueAmount.amount().compareTo(buckets) != 0
        || outstandingAmount.amount().compareTo(currentAmount.amount().add(overdueAmount.amount()))
            != 0) {
      throw new IllegalArgumentException("Copilot summary amount invariants are inconsistent");
    }
  }
}
