package io.github.khaledshawki.eoc.operations.application.model.querying;

import io.github.khaledshawki.eoc.operations.domain.model.Payment;
import java.util.List;
import java.util.Objects;

public record PaymentQueryPage(
    List<Payment> payments, int pageNumber, int pageSize, long totalElements) {

  public PaymentQueryPage {
    Objects.requireNonNull(payments, "Payment query page payments cannot be null");
    if (payments.stream().anyMatch(Objects::isNull)) {
      throw new NullPointerException("Payment query page payments cannot contain null");
    }
    payments = List.copyOf(payments);
    if (pageNumber < 0) {
      throw new IllegalArgumentException("Payment query page number cannot be negative");
    }
    if (pageSize < 1) {
      throw new IllegalArgumentException("Payment query page size must be positive");
    }
    if (totalElements < 0) {
      throw new IllegalArgumentException("Payment query total elements cannot be negative");
    }
    if (payments.size() > pageSize) {
      throw new IllegalArgumentException("Payment query page cannot exceed its requested size");
    }
    if (payments.size() > totalElements) {
      throw new IllegalArgumentException("Payment query page cannot exceed its total elements");
    }
  }

  public int totalPages() {
    long pages = totalElements / pageSize + (totalElements % pageSize == 0 ? 0 : 1);
    if (pages > Integer.MAX_VALUE) {
      throw new IllegalStateException("Payment query total pages exceed the supported range");
    }
    return (int) pages;
  }

  public boolean hasNext() {
    return pageNumber + 1L < totalPages();
  }

  public boolean hasPrevious() {
    return pageNumber > 0 && totalElements > 0;
  }
}
