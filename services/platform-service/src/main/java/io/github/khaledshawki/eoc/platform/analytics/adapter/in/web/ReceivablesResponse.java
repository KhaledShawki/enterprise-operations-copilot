package io.github.khaledshawki.eoc.platform.analytics.adapter.in.web;

import io.github.khaledshawki.eoc.analytics.application.port.in.ReceivablePageResult;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

public record ReceivablesResponse(
    List<ReceivableResponse> receivables,
    int page,
    int size,
    long totalElements,
    long totalPages,
    LocalDate businessDate,
    boolean hasNext,
    boolean hasPrevious) {

  public ReceivablesResponse {
    Objects.requireNonNull(receivables, "Receivables response content cannot be null");
    receivables = List.copyOf(receivables);
    Objects.requireNonNull(businessDate, "Receivables response business date cannot be null");
  }

  static ReceivablesResponse from(ReceivablePageResult result) {
    Objects.requireNonNull(result, "Receivable page result cannot be null");
    return new ReceivablesResponse(
        result.receivables().stream().map(ReceivableResponse::from).toList(),
        result.pageNumber(),
        result.pageSize(),
        result.totalElements(),
        result.totalPages(),
        result.businessDate(),
        result.hasNext(),
        result.hasPrevious());
  }
}
