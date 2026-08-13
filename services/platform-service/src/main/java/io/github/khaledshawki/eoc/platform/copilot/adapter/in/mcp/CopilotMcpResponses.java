package io.github.khaledshawki.eoc.platform.copilot.adapter.in.mcp;

import io.github.khaledshawki.eoc.copilot.application.model.CopilotCustomer;
import io.github.khaledshawki.eoc.copilot.application.model.CopilotEvidence;
import io.github.khaledshawki.eoc.copilot.application.model.CopilotMoney;
import io.github.khaledshawki.eoc.copilot.application.model.CopilotReceivable;
import io.github.khaledshawki.eoc.copilot.application.model.CopilotReceivableCurrencySummary;
import io.github.khaledshawki.eoc.copilot.application.model.CopilotReceivablePage;
import io.github.khaledshawki.eoc.copilot.application.model.CopilotReceivablesSummary;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class CopilotMcpResponses {

  private CopilotMcpResponses() {}

  static ReceivableResponse from(CopilotReceivable receivable) {
    return new ReceivableResponse(
        receivable.tenantId(),
        receivable.invoiceId(),
        from(receivable.customer()),
        receivable.invoiceNumber(),
        from(receivable.originalAmount()),
        from(receivable.paidAmount()),
        from(receivable.outstandingAmount()),
        receivable.issueDate(),
        receivable.dueDate(),
        receivable.businessDate(),
        receivable.status().name(),
        receivable.cancelled(),
        receivable.overdue(),
        from(receivable.evidence()));
  }

  static ReceivablePageResponse from(CopilotReceivablePage page) {
    return new ReceivablePageResponse(
        page.receivables().stream().map(CopilotMcpResponses::from).toList(),
        page.pageNumber(),
        page.pageSize(),
        page.totalElements(),
        page.totalPages(),
        page.businessDate(),
        page.hasNext(),
        page.hasPrevious());
  }

  static ReceivablesSummaryResponse from(CopilotReceivablesSummary summary) {
    return new ReceivablesSummaryResponse(
        summary.tenantId(),
        summary.businessDate(),
        summary.invoiceCount(),
        summary.openCount(),
        summary.overdueCount(),
        summary.currencies().stream().map(CopilotMcpResponses::from).toList());
  }

  private static CustomerResponse from(CopilotCustomer customer) {
    return new CustomerResponse(
        customer.customerId(),
        customer.projected(),
        customer.partnerNumber().orElse(null),
        customer.displayName().orElse(null));
  }

  private static MoneyResponse from(CopilotMoney money) {
    return new MoneyResponse(money.amount(), money.currency());
  }

  private static EvidenceResponse from(CopilotEvidence evidence) {
    return new EvidenceResponse(
        evidence.eventId(), evidence.aggregateVersion(), evidence.occurredAt());
  }

  private static CurrencySummaryResponse from(CopilotReceivableCurrencySummary summary) {
    return new CurrencySummaryResponse(
        summary.currency(),
        summary.invoiceCount(),
        summary.openCount(),
        summary.overdueCount(),
        from(summary.outstandingAmount()),
        from(summary.overdueAmount()),
        from(summary.currentAmount()),
        from(summary.days1To30OverdueAmount()),
        from(summary.days31To60OverdueAmount()),
        from(summary.days61To90OverdueAmount()),
        from(summary.days91PlusOverdueAmount()));
  }

  public record MoneyResponse(BigDecimal amount, String currency) {}

  public record CustomerResponse(
      UUID customerId, boolean projected, String partnerNumber, String displayName) {}

  public record EvidenceResponse(UUID eventId, long aggregateVersion, Instant occurredAt) {}

  public record ReceivableResponse(
      UUID tenantId,
      UUID invoiceId,
      CustomerResponse customer,
      String invoiceNumber,
      MoneyResponse originalAmount,
      MoneyResponse paidAmount,
      MoneyResponse outstandingAmount,
      LocalDate issueDate,
      LocalDate dueDate,
      LocalDate businessDate,
      String status,
      boolean cancelled,
      boolean overdue,
      EvidenceResponse evidence) {}

  public record ReceivablePageResponse(
      List<ReceivableResponse> receivables,
      int pageNumber,
      int pageSize,
      long totalElements,
      long totalPages,
      LocalDate businessDate,
      boolean hasNext,
      boolean hasPrevious) {}

  public record CurrencySummaryResponse(
      String currency,
      long invoiceCount,
      long openCount,
      long overdueCount,
      MoneyResponse outstandingAmount,
      MoneyResponse overdueAmount,
      MoneyResponse currentAmount,
      MoneyResponse days1To30OverdueAmount,
      MoneyResponse days31To60OverdueAmount,
      MoneyResponse days61To90OverdueAmount,
      MoneyResponse days91PlusOverdueAmount) {}

  public record ReceivablesSummaryResponse(
      UUID tenantId,
      LocalDate businessDate,
      long invoiceCount,
      long openCount,
      long overdueCount,
      List<CurrencySummaryResponse> currencies) {}
}
