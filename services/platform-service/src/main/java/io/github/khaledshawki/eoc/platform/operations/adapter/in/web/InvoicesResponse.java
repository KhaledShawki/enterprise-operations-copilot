package io.github.khaledshawki.eoc.platform.operations.adapter.in.web;

import io.github.khaledshawki.eoc.operations.application.port.in.InvoicePageResult;
import java.util.List;
import java.util.Objects;

public record InvoicesResponse(
    List<InvoiceResponse> invoices,
    int page,
    int size,
    long totalElements,
    int totalPages,
    java.time.LocalDate businessDate,
    boolean hasNext,
    boolean hasPrevious) {

  public InvoicesResponse {
    Objects.requireNonNull(invoices, "Invoice responses cannot be null");
    Objects.requireNonNull(businessDate, "Invoice response business date cannot be null");
    if (invoices.stream().anyMatch(Objects::isNull)) {
      throw new NullPointerException("Invoice responses cannot contain null");
    }
    invoices = List.copyOf(invoices);
  }

  static InvoicesResponse from(InvoicePageResult result) {
    Objects.requireNonNull(result, "Invoice page result cannot be null");
    return new InvoicesResponse(
        result.invoices().stream().map(InvoiceResponse::from).toList(),
        result.pageNumber(),
        result.pageSize(),
        result.totalElements(),
        result.totalPages(),
        result.businessDate(),
        result.hasNext(),
        result.hasPrevious());
  }
}
