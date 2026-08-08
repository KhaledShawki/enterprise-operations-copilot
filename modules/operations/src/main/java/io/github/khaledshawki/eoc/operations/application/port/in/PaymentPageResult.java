package io.github.khaledshawki.eoc.operations.application.port.in;

import io.github.khaledshawki.eoc.operations.application.model.querying.PaymentQueryPage;
import java.util.List;
import java.util.Objects;

public record PaymentPageResult(
    List<PaymentResult> payments,
    int pageNumber,
    int pageSize,
    long totalElements,
    int totalPages,
    boolean hasNext,
    boolean hasPrevious) {

  public PaymentPageResult {
    Objects.requireNonNull(payments, "Payment page results cannot be null");
    if (payments.stream().anyMatch(Objects::isNull)) {
      throw new NullPointerException("Payment page results cannot contain null");
    }
    payments = List.copyOf(payments);
    if (pageNumber < 0 || pageSize < 1 || totalElements < 0 || totalPages < 0) {
      throw new IllegalArgumentException("Payment page metadata cannot be negative");
    }
    if (payments.size() > pageSize || payments.size() > totalElements) {
      throw new IllegalArgumentException("Payment page result counts are inconsistent");
    }
  }

  public static PaymentPageResult from(PaymentQueryPage page) {
    Objects.requireNonNull(page, "Payment query page cannot be null");
    return new PaymentPageResult(
        page.payments().stream().map(PaymentResult::from).toList(),
        page.pageNumber(),
        page.pageSize(),
        page.totalElements(),
        page.totalPages(),
        page.hasNext(),
        page.hasPrevious());
  }
}
