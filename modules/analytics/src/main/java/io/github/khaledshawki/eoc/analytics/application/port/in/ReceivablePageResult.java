package io.github.khaledshawki.eoc.analytics.application.port.in;

import io.github.khaledshawki.eoc.analytics.application.model.querying.ReceivablePage;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

public record ReceivablePageResult(
    List<ReceivableResult> receivables,
    int pageNumber,
    int pageSize,
    long totalElements,
    long totalPages,
    LocalDate businessDate,
    boolean hasNext,
    boolean hasPrevious) {

  public ReceivablePageResult {
    Objects.requireNonNull(receivables, "Receivable page results cannot be null");
    if (receivables.stream().anyMatch(Objects::isNull)) {
      throw new NullPointerException("Receivable page results cannot contain null");
    }
    receivables = List.copyOf(receivables);
    Objects.requireNonNull(businessDate, "Receivable page business date cannot be null");
    if (pageNumber < 0 || pageSize < 1 || totalElements < 0 || totalPages < 0) {
      throw new IllegalArgumentException("Receivable page metadata cannot be negative");
    }
    if (receivables.size() > pageSize || receivables.size() > totalElements) {
      throw new IllegalArgumentException("Receivable page result counts are inconsistent");
    }
  }

  public static ReceivablePageResult from(ReceivablePage page, LocalDate businessDate) {
    Objects.requireNonNull(page, "Receivable page cannot be null");
    Objects.requireNonNull(businessDate, "Receivable business date cannot be null");
    return new ReceivablePageResult(
        page.receivables().stream()
            .map(receivable -> ReceivableResult.from(receivable, businessDate))
            .toList(),
        page.pageNumber(),
        page.pageSize(),
        page.totalElements(),
        page.totalPages(),
        businessDate,
        page.hasNext(),
        page.hasPrevious());
  }
}
