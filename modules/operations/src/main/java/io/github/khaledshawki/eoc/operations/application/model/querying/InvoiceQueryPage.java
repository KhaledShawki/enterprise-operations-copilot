package io.github.khaledshawki.eoc.operations.application.model.querying;

import io.github.khaledshawki.eoc.operations.domain.model.Invoice;
import java.util.List;
import java.util.Objects;

public record InvoiceQueryPage(
    List<Invoice> invoices, int pageNumber, int pageSize, long totalElements) {

  public InvoiceQueryPage {
    Objects.requireNonNull(invoices, "Invoice query page invoices cannot be null");
    if (invoices.stream().anyMatch(Objects::isNull)) {
      throw new NullPointerException("Invoice query page invoices cannot contain null");
    }
    invoices = List.copyOf(invoices);
    if (pageNumber < 0) {
      throw new IllegalArgumentException("Invoice query page number cannot be negative");
    }
    if (pageSize < 1) {
      throw new IllegalArgumentException("Invoice query page size must be positive");
    }
    if (totalElements < 0) {
      throw new IllegalArgumentException("Invoice query total elements cannot be negative");
    }
    if (invoices.size() > pageSize) {
      throw new IllegalArgumentException("Invoice query page cannot exceed its requested size");
    }
    if (invoices.size() > totalElements) {
      throw new IllegalArgumentException("Invoice query page cannot exceed its total elements");
    }
  }

  public int totalPages() {
    long pages = totalElements / pageSize + (totalElements % pageSize == 0 ? 0 : 1);
    if (pages > Integer.MAX_VALUE) {
      throw new IllegalStateException("Invoice query total pages exceed the supported range");
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
