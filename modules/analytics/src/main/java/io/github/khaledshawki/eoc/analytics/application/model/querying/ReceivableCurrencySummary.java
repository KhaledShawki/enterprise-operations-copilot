package io.github.khaledshawki.eoc.analytics.application.model.querying;

import io.github.khaledshawki.eoc.analytics.domain.model.AnalyticsMoney;
import io.github.khaledshawki.eoc.analytics.domain.model.CurrencyCode;
import java.math.BigDecimal;
import java.util.Objects;

public record ReceivableCurrencySummary(
    CurrencyCode currency,
    long invoiceCount,
    long openCount,
    long overdueCount,
    AnalyticsMoney outstandingAmount,
    AnalyticsMoney overdueAmount,
    AnalyticsMoney currentAmount,
    AnalyticsMoney days1To30OverdueAmount,
    AnalyticsMoney days31To60OverdueAmount,
    AnalyticsMoney days61To90OverdueAmount,
    AnalyticsMoney days91PlusOverdueAmount) {

  public ReceivableCurrencySummary {
    Objects.requireNonNull(currency, "Receivable summary currency cannot be null");
    requireCounts(invoiceCount, openCount, overdueCount);
    requireMoney(outstandingAmount, currency, "outstanding amount");
    requireMoney(overdueAmount, currency, "overdue amount");
    requireMoney(currentAmount, currency, "current amount");
    requireMoney(days1To30OverdueAmount, currency, "1-30 day overdue amount");
    requireMoney(days31To60OverdueAmount, currency, "31-60 day overdue amount");
    requireMoney(days61To90OverdueAmount, currency, "61-90 day overdue amount");
    requireMoney(days91PlusOverdueAmount, currency, "91+ day overdue amount");

    BigDecimal overdueBuckets =
        days1To30OverdueAmount
            .amount()
            .add(days31To60OverdueAmount.amount())
            .add(days61To90OverdueAmount.amount())
            .add(days91PlusOverdueAmount.amount());
    if (overdueAmount.amount().compareTo(overdueBuckets) != 0) {
      throw new IllegalArgumentException(
          "Receivable summary overdue amount must equal its aging buckets");
    }
    if (outstandingAmount.amount().compareTo(currentAmount.amount().add(overdueAmount.amount()))
        != 0) {
      throw new IllegalArgumentException(
          "Receivable summary outstanding amount must equal current plus overdue amount");
    }
    if (openCount == 0 && outstandingAmount.isPositive()) {
      throw new IllegalArgumentException(
          "Receivable summary cannot have outstanding amount without open receivables");
    }
    if (overdueCount == 0 && overdueAmount.isPositive()) {
      throw new IllegalArgumentException(
          "Receivable summary cannot have overdue amount without overdue receivables");
    }
  }

  private static void requireCounts(long invoiceCount, long openCount, long overdueCount) {
    if (invoiceCount < 0 || openCount < 0 || overdueCount < 0) {
      throw new IllegalArgumentException("Receivable summary counts cannot be negative");
    }
    if (openCount > invoiceCount || overdueCount > openCount) {
      throw new IllegalArgumentException("Receivable summary counts are inconsistent");
    }
  }

  private static void requireMoney(AnalyticsMoney money, CurrencyCode currency, String field) {
    Objects.requireNonNull(money, "Receivable summary " + field + " cannot be null");
    if (!money.currency().equals(currency)) {
      throw new IllegalArgumentException("Receivable summary currencies must match");
    }
    if (money.isNegative()) {
      throw new IllegalArgumentException("Receivable summary " + field + " cannot be negative");
    }
  }
}
