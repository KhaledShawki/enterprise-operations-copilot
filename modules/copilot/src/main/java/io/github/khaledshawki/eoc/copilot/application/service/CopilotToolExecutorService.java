package io.github.khaledshawki.eoc.copilot.application.service;

import io.github.khaledshawki.eoc.copilot.application.exception.CopilotToolAccessDeniedException;
import io.github.khaledshawki.eoc.copilot.application.exception.CopilotToolDataCorruptedException;
import io.github.khaledshawki.eoc.copilot.application.model.CopilotExecutionContext;
import io.github.khaledshawki.eoc.copilot.application.model.CopilotReceivable;
import io.github.khaledshawki.eoc.copilot.application.model.CopilotReceivablePage;
import io.github.khaledshawki.eoc.copilot.application.model.CopilotReceivablesSummary;
import io.github.khaledshawki.eoc.copilot.application.model.GetReceivableToolRequest;
import io.github.khaledshawki.eoc.copilot.application.model.GetReceivablesSummaryToolRequest;
import io.github.khaledshawki.eoc.copilot.application.model.ListReceivablesToolRequest;
import io.github.khaledshawki.eoc.copilot.application.model.ReceivableListCriteria;
import io.github.khaledshawki.eoc.copilot.application.port.in.ExecuteCopilotToolUseCase;
import io.github.khaledshawki.eoc.copilot.application.port.out.CopilotReceivablesAuthorizationPort;
import io.github.khaledshawki.eoc.copilot.application.port.out.CopilotReceivablesDataPort;
import java.time.Clock;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Objects;

public final class CopilotToolExecutorService implements ExecuteCopilotToolUseCase {
  private final CopilotReceivablesAuthorizationPort authorizationPort;
  private final CopilotReceivablesDataPort dataPort;
  private final Clock clock;

  public CopilotToolExecutorService(
      CopilotReceivablesAuthorizationPort authorizationPort,
      CopilotReceivablesDataPort dataPort,
      Clock clock) {
    this.authorizationPort =
        Objects.requireNonNull(authorizationPort, "Copilot authorization port cannot be null");
    this.dataPort = Objects.requireNonNull(dataPort, "Copilot data port cannot be null");
    this.clock = Objects.requireNonNull(clock, "Copilot clock cannot be null");
  }

  @Override
  public CopilotReceivable execute(
      CopilotExecutionContext context, GetReceivableToolRequest request) {
    requireContextAndRequest(context, request);
    authorize(context);
    LocalDate businessDate = request.businessDate().orElseGet(() -> LocalDate.now(clock));
    CopilotReceivable receivable =
        Objects.requireNonNull(
            dataPort.getReceivable(context.tenantId(), request.invoiceId(), businessDate),
            "Copilot data port returned null receivable");
    if (!context.tenantId().equals(receivable.tenantId())
        || !request.invoiceId().equals(receivable.invoiceId())
        || !businessDate.equals(receivable.businessDate())) {
      throw corrupted("Copilot receivable result violates the requested execution scope");
    }
    return receivable;
  }

  @Override
  public CopilotReceivablePage execute(
      CopilotExecutionContext context, ListReceivablesToolRequest request) {
    requireContextAndRequest(context, request);
    authorize(context);
    LocalDate businessDate = request.businessDate().orElseGet(() -> LocalDate.now(clock));
    ReceivableListCriteria criteria = ReceivableListCriteria.from(request, businessDate);
    CopilotReceivablePage page =
        Objects.requireNonNull(
            dataPort.listReceivables(context.tenantId(), criteria),
            "Copilot data port returned null receivable page");
    validatePage(context, request, businessDate, page);
    return page;
  }

  @Override
  public CopilotReceivablesSummary execute(
      CopilotExecutionContext context, GetReceivablesSummaryToolRequest request) {
    requireContextAndRequest(context, request);
    authorize(context);
    LocalDate businessDate = request.businessDate().orElseGet(() -> LocalDate.now(clock));
    CopilotReceivablesSummary summary =
        Objects.requireNonNull(
            dataPort.getReceivablesSummary(context.tenantId(), businessDate),
            "Copilot data port returned null receivables summary");
    if (!context.tenantId().equals(summary.tenantId())
        || !businessDate.equals(summary.businessDate())) {
      throw corrupted("Copilot receivables summary violates the requested execution scope");
    }
    return summary;
  }

  private void authorize(CopilotExecutionContext context) {
    if (!authorizationPort.mayReadReceivables(context)) {
      throw new CopilotToolAccessDeniedException();
    }
  }

  private static void validatePage(
      CopilotExecutionContext context,
      ListReceivablesToolRequest request,
      LocalDate businessDate,
      CopilotReceivablePage page) {
    if (page.pageNumber() != request.pageNumber()
        || page.pageSize() != request.pageSize()
        || !businessDate.equals(page.businessDate())) {
      throw corrupted("Copilot receivable page metadata violates the requested scope");
    }
    var seen = new HashSet<java.util.UUID>();
    for (CopilotReceivable receivable : page.receivables()) {
      if (!context.tenantId().equals(receivable.tenantId())
          || !businessDate.equals(receivable.businessDate())
          || !seen.add(receivable.invoiceId())
          || request
              .customerId()
              .filter(id -> !id.equals(receivable.customer().customerId()))
              .isPresent()
          || (!request.statuses().isEmpty() && !request.statuses().contains(receivable.status()))
          || request.overdue().filter(value -> value != receivable.overdue()).isPresent()) {
        throw corrupted("Copilot receivable page contains data outside the requested scope");
      }
    }
  }

  private static void requireContextAndRequest(CopilotExecutionContext context, Object request) {
    Objects.requireNonNull(context, "Copilot execution context cannot be null");
    Objects.requireNonNull(request, "Copilot tool request cannot be null");
  }

  private static CopilotToolDataCorruptedException corrupted(String message) {
    return new CopilotToolDataCorruptedException(message, null);
  }
}
