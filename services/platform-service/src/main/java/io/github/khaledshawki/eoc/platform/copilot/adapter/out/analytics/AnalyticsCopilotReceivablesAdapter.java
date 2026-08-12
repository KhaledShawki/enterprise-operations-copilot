package io.github.khaledshawki.eoc.platform.copilot.adapter.out.analytics;

import io.github.khaledshawki.eoc.analytics.application.exception.AnalyticsProjectionStateCorruptedException;
import io.github.khaledshawki.eoc.analytics.application.exception.AnalyticsReadUnavailableException;
import io.github.khaledshawki.eoc.analytics.application.exception.InvalidReceivableQueryException;
import io.github.khaledshawki.eoc.analytics.application.exception.ReceivableNotFoundException;
import io.github.khaledshawki.eoc.analytics.application.model.querying.ReceivableCurrencySummary;
import io.github.khaledshawki.eoc.analytics.application.model.querying.ReceivableSortField;
import io.github.khaledshawki.eoc.analytics.application.model.querying.SortDirection;
import io.github.khaledshawki.eoc.analytics.application.port.in.GetReceivableQuery;
import io.github.khaledshawki.eoc.analytics.application.port.in.GetReceivableUseCase;
import io.github.khaledshawki.eoc.analytics.application.port.in.GetReceivablesSummaryQuery;
import io.github.khaledshawki.eoc.analytics.application.port.in.GetReceivablesSummaryUseCase;
import io.github.khaledshawki.eoc.analytics.application.port.in.ListReceivablesQuery;
import io.github.khaledshawki.eoc.analytics.application.port.in.ListReceivablesUseCase;
import io.github.khaledshawki.eoc.analytics.application.port.in.ReceivablePageResult;
import io.github.khaledshawki.eoc.analytics.application.port.in.ReceivableResult;
import io.github.khaledshawki.eoc.analytics.application.port.in.ReceivablesSummaryResult;
import io.github.khaledshawki.eoc.analytics.domain.model.AnalyticsMoney;
import io.github.khaledshawki.eoc.analytics.domain.model.InvoiceReceivableStatus;
import io.github.khaledshawki.eoc.copilot.application.exception.CopilotToolDataCorruptedException;
import io.github.khaledshawki.eoc.copilot.application.exception.CopilotToolDataNotFoundException;
import io.github.khaledshawki.eoc.copilot.application.exception.CopilotToolDataUnavailableException;
import io.github.khaledshawki.eoc.copilot.application.model.CopilotCustomer;
import io.github.khaledshawki.eoc.copilot.application.model.CopilotEvidence;
import io.github.khaledshawki.eoc.copilot.application.model.CopilotMoney;
import io.github.khaledshawki.eoc.copilot.application.model.CopilotReceivable;
import io.github.khaledshawki.eoc.copilot.application.model.CopilotReceivableCurrencySummary;
import io.github.khaledshawki.eoc.copilot.application.model.CopilotReceivablePage;
import io.github.khaledshawki.eoc.copilot.application.model.CopilotReceivablesSummary;
import io.github.khaledshawki.eoc.copilot.application.model.ReceivableListCriteria;
import io.github.khaledshawki.eoc.copilot.application.model.ReceivableStatus;
import io.github.khaledshawki.eoc.copilot.application.port.out.CopilotReceivablesDataPort;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

public final class AnalyticsCopilotReceivablesAdapter implements CopilotReceivablesDataPort {
  private final GetReceivableUseCase getReceivableUseCase;
  private final ListReceivablesUseCase listReceivablesUseCase;
  private final GetReceivablesSummaryUseCase getReceivablesSummaryUseCase;

  public AnalyticsCopilotReceivablesAdapter(
      GetReceivableUseCase getReceivableUseCase,
      ListReceivablesUseCase listReceivablesUseCase,
      GetReceivablesSummaryUseCase getReceivablesSummaryUseCase) {
    this.getReceivableUseCase = Objects.requireNonNull(getReceivableUseCase);
    this.listReceivablesUseCase = Objects.requireNonNull(listReceivablesUseCase);
    this.getReceivablesSummaryUseCase = Objects.requireNonNull(getReceivablesSummaryUseCase);
  }

  @Override
  public CopilotReceivable getReceivable(UUID tenantId, UUID invoiceId, LocalDate businessDate) {
    return translate(
        () ->
            toCopilot(
                getReceivableUseCase.get(
                    new GetReceivableQuery(tenantId, invoiceId, businessDate))));
  }

  @Override
  public CopilotReceivablePage listReceivables(UUID tenantId, ReceivableListCriteria criteria) {
    return translate(
        () -> {
          var query =
              new ListReceivablesQuery(
                  tenantId,
                  criteria.customerId(),
                  criteria.statuses().stream()
                      .map(status -> InvoiceReceivableStatus.valueOf(status.name()))
                      .collect(java.util.stream.Collectors.toUnmodifiableSet()),
                  criteria.overdue(),
                  criteria.businessDate(),
                  criteria.pageNumber(),
                  criteria.pageSize(),
                  ReceivableSortField.valueOf(criteria.sortField().name()),
                  SortDirection.valueOf(criteria.sortDirection().name()));
          ReceivablePageResult result = listReceivablesUseCase.list(query);
          return new CopilotReceivablePage(
              result.receivables().stream()
                  .map(AnalyticsCopilotReceivablesAdapter::toCopilot)
                  .toList(),
              result.pageNumber(),
              result.pageSize(),
              result.totalElements(),
              result.totalPages(),
              result.businessDate(),
              result.hasNext(),
              result.hasPrevious());
        });
  }

  @Override
  public CopilotReceivablesSummary getReceivablesSummary(UUID tenantId, LocalDate businessDate) {
    return translate(
        () -> {
          ReceivablesSummaryResult result =
              getReceivablesSummaryUseCase.get(
                  new GetReceivablesSummaryQuery(tenantId, businessDate));
          return new CopilotReceivablesSummary(
              result.tenantId(),
              result.businessDate(),
              result.invoiceCount(),
              result.openCount(),
              result.overdueCount(),
              result.currencies().stream()
                  .map(AnalyticsCopilotReceivablesAdapter::toCopilot)
                  .toList());
        });
  }

  private static CopilotReceivable toCopilot(ReceivableResult result) {
    var customer = result.customer();
    var source = result.source();
    return new CopilotReceivable(
        result.tenantId(),
        result.invoiceId(),
        new CopilotCustomer(
            customer.customerId(),
            customer.projected(),
            customer.partnerNumber(),
            customer.displayName()),
        result.invoiceNumber(),
        money(result.originalAmount()),
        money(result.paidAmount()),
        money(result.outstandingAmount()),
        result.issueDate(),
        result.dueDate(),
        result.businessDate(),
        ReceivableStatus.valueOf(result.status().name()),
        result.cancelled(),
        result.overdue(),
        new CopilotEvidence(source.eventId(), source.aggregateVersion(), source.occurredAt()));
  }

  private static CopilotReceivableCurrencySummary toCopilot(ReceivableCurrencySummary summary) {
    return new CopilotReceivableCurrencySummary(
        summary.currency().value(),
        summary.invoiceCount(),
        summary.openCount(),
        summary.overdueCount(),
        money(summary.outstandingAmount()),
        money(summary.overdueAmount()),
        money(summary.currentAmount()),
        money(summary.days1To30OverdueAmount()),
        money(summary.days31To60OverdueAmount()),
        money(summary.days61To90OverdueAmount()),
        money(summary.days91PlusOverdueAmount()));
  }

  private static CopilotMoney money(AnalyticsMoney money) {
    return new CopilotMoney(money.amount(), money.currency().value());
  }

  private static <T> T translate(java.util.function.Supplier<T> action) {
    try {
      return action.get();
    } catch (ReceivableNotFoundException exception) {
      throw new CopilotToolDataNotFoundException(exception.getMessage(), exception);
    } catch (AnalyticsReadUnavailableException exception) {
      throw new CopilotToolDataUnavailableException(exception);
    } catch (AnalyticsProjectionStateCorruptedException
        | InvalidReceivableQueryException exception) {
      throw new CopilotToolDataCorruptedException(
          "Analytics receivables query contract failed", exception);
    } catch (IllegalArgumentException | NullPointerException | ArithmeticException exception) {
      throw new CopilotToolDataCorruptedException(
          "Analytics receivables result violated the Copilot contract", exception);
    }
  }
}
