package io.github.khaledshawki.eoc.platform.analytics.adapter.in.web;

import io.github.khaledshawki.eoc.analytics.application.model.querying.ReceivableCurrencySummary;
import io.github.khaledshawki.eoc.analytics.application.port.in.ReceivablesSummaryResult;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record ReceivablesSummaryResponse(
    UUID tenantId,
    LocalDate businessDate,
    long invoiceCount,
    long openCount,
    long overdueCount,
    List<CurrencySummaryResponse> currencies) {

  public ReceivablesSummaryResponse {
    Objects.requireNonNull(tenantId, "Receivables summary response tenant id cannot be null");
    Objects.requireNonNull(
        businessDate, "Receivables summary response business date cannot be null");
    Objects.requireNonNull(currencies, "Receivables summary response currencies cannot be null");
    currencies = List.copyOf(currencies);
  }

  static ReceivablesSummaryResponse from(ReceivablesSummaryResult result) {
    Objects.requireNonNull(result, "Receivables summary result cannot be null");
    return new ReceivablesSummaryResponse(
        result.tenantId(),
        result.businessDate(),
        result.invoiceCount(),
        result.openCount(),
        result.overdueCount(),
        result.currencies().stream().map(CurrencySummaryResponse::from).toList());
  }

  public record CurrencySummaryResponse(
      String currency,
      long invoiceCount,
      long openCount,
      long overdueCount,
      BigDecimal outstandingAmount,
      BigDecimal overdueAmount,
      AgingResponse aging) {

    static CurrencySummaryResponse from(ReceivableCurrencySummary summary) {
      return new CurrencySummaryResponse(
          summary.currency().value(),
          summary.invoiceCount(),
          summary.openCount(),
          summary.overdueCount(),
          summary.outstandingAmount().amount(),
          summary.overdueAmount().amount(),
          new AgingResponse(
              summary.currentAmount().amount(),
              summary.days1To30OverdueAmount().amount(),
              summary.days31To60OverdueAmount().amount(),
              summary.days61To90OverdueAmount().amount(),
              summary.days91PlusOverdueAmount().amount()));
    }
  }

  public record AgingResponse(
      BigDecimal currentAmount,
      BigDecimal days1To30OverdueAmount,
      BigDecimal days31To60OverdueAmount,
      BigDecimal days61To90OverdueAmount,
      BigDecimal days91PlusOverdueAmount) {

    public AgingResponse {
      Objects.requireNonNull(currentAmount, "Current aging amount cannot be null");
      Objects.requireNonNull(
          days1To30OverdueAmount, "1-30 day overdue aging amount cannot be null");
      Objects.requireNonNull(
          days31To60OverdueAmount, "31-60 day overdue aging amount cannot be null");
      Objects.requireNonNull(
          days61To90OverdueAmount, "61-90 day overdue aging amount cannot be null");
      Objects.requireNonNull(
          days91PlusOverdueAmount, "91+ day overdue aging amount cannot be null");
    }
  }
}
