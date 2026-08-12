package io.github.khaledshawki.eoc.copilot.application.model;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

public record CopilotReceivablePage(
    List<CopilotReceivable> receivables,
    int pageNumber,
    int pageSize,
    long totalElements,
    long totalPages,
    LocalDate businessDate,
    boolean hasNext,
    boolean hasPrevious) {
  public CopilotReceivablePage {
    Objects.requireNonNull(receivables, "Copilot receivables cannot be null");
    if (receivables.stream().anyMatch(Objects::isNull)) {
      throw new NullPointerException("Copilot receivables cannot contain null values");
    }
    receivables = List.copyOf(receivables);
    if (pageNumber < 0
        || pageSize < 1
        || totalElements < 0
        || totalPages < 0
        || receivables.size() > pageSize
        || receivables.size() > totalElements) {
      throw new IllegalArgumentException("Copilot receivable page metadata is inconsistent");
    }
    long expectedTotalPages = totalElements / pageSize + (totalElements % pageSize == 0 ? 0 : 1);
    if (totalPages != expectedTotalPages) {
      throw new IllegalArgumentException("Copilot receivable total pages are inconsistent");
    }
    if (new HashSet<>(receivables.stream().map(CopilotReceivable::invoiceId).toList()).size()
        != receivables.size()) {
      throw new IllegalArgumentException(
          "Copilot receivable page cannot contain duplicate invoices");
    }
    Objects.requireNonNull(businessDate, "Copilot receivable page business date cannot be null");
  }
}
