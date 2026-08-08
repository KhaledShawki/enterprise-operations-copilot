package io.github.khaledshawki.eoc.platform.operations.adapter.in.web;

import io.github.khaledshawki.eoc.operations.application.port.in.PaymentPageResult;
import java.util.List;
import java.util.Objects;

public record PaymentsResponse(
    List<PaymentResponse> payments,
    int page,
    int size,
    long totalElements,
    int totalPages,
    boolean hasNext,
    boolean hasPrevious) {

  public PaymentsResponse {
    Objects.requireNonNull(payments, "Payment responses cannot be null");
    if (payments.stream().anyMatch(Objects::isNull)) {
      throw new NullPointerException("Payment responses cannot contain null");
    }
    payments = List.copyOf(payments);
  }

  static PaymentsResponse from(PaymentPageResult result) {
    Objects.requireNonNull(result, "Payment page result cannot be null");
    return new PaymentsResponse(
        result.payments().stream().map(PaymentResponse::from).toList(),
        result.pageNumber(),
        result.pageSize(),
        result.totalElements(),
        result.totalPages(),
        result.hasNext(),
        result.hasPrevious());
  }
}
