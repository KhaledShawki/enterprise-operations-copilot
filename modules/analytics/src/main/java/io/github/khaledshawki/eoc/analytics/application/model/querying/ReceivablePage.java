package io.github.khaledshawki.eoc.analytics.application.model.querying;

import java.util.List;
import java.util.Objects;

public record ReceivablePage(
    List<ReceivableSnapshot> receivables, int pageNumber, int pageSize, long totalElements) {

  public ReceivablePage {
    Objects.requireNonNull(receivables, "Receivable page content cannot be null");
    if (receivables.stream().anyMatch(Objects::isNull)) {
      throw new NullPointerException("Receivable page content cannot contain null");
    }
    receivables = List.copyOf(receivables);
    if (pageNumber < 0) {
      throw new IllegalArgumentException("Receivable page number cannot be negative");
    }
    if (pageSize < 1 || pageSize > ReceivableQueryCriteria.MAX_PAGE_SIZE) {
      throw new IllegalArgumentException("Receivable page size is outside the supported range");
    }
    if (totalElements < 0) {
      throw new IllegalArgumentException("Receivable page total elements cannot be negative");
    }
    if (receivables.size() > pageSize || receivables.size() > totalElements) {
      throw new IllegalArgumentException("Receivable page counts are inconsistent");
    }
  }

  public long totalPages() {
    return totalElements == 0 ? 0 : ((totalElements - 1) / pageSize) + 1;
  }

  public boolean hasNext() {
    return ((long) pageNumber + 1) * pageSize < totalElements;
  }

  public boolean hasPrevious() {
    return pageNumber > 0 && totalElements > 0;
  }
}
