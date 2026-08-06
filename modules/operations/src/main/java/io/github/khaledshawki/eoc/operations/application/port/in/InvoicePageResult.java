package io.github.khaledshawki.eoc.operations.application.port.in;

import io.github.khaledshawki.eoc.operations.application.model.querying.InvoiceQueryPage;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

public record InvoicePageResult(
    List<InvoiceResult> invoices,
    int pageNumber,
    int pageSize,
    long totalElements,
    int totalPages,
    LocalDate businessDate,
    boolean hasNext,
    boolean hasPrevious) {

  public InvoicePageResult {
    Objects.requireNonNull(invoices, "Invoice page results cannot be null");
    if (invoices.stream().anyMatch(Objects::isNull)) {
      throw new NullPointerException("Invoice page results cannot contain null");
    }
    invoices = List.copyOf(invoices);
    Objects.requireNonNull(businessDate, "Invoice page business date cannot be null");
    if (pageNumber < 0 || pageSize < 1 || totalElements < 0 || totalPages < 0) {
      throw new IllegalArgumentException("Invoice page metadata cannot be negative");
    }
    if (invoices.size() > pageSize || invoices.size() > totalElements) {
      throw new IllegalArgumentException("Invoice page result counts are inconsistent");
    }
  }

  public static InvoicePageResult from(InvoiceQueryPage page, LocalDate businessDate) {
    Objects.requireNonNull(page, "Invoice query page cannot be null");
    Objects.requireNonNull(businessDate, "Invoice business date cannot be null");
    return new InvoicePageResult(
        page.invoices().stream().map(invoice -> InvoiceResult.from(invoice, businessDate)).toList(),
        page.pageNumber(),
        page.pageSize(),
        page.totalElements(),
        page.totalPages(),
        businessDate,
        page.hasNext(),
        page.hasPrevious());
  }
}
