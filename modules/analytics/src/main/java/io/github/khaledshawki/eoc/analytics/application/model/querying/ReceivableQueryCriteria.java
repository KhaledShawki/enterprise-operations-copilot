package io.github.khaledshawki.eoc.analytics.application.model.querying;

import io.github.khaledshawki.eoc.analytics.domain.model.AnalyticsTenantId;
import io.github.khaledshawki.eoc.analytics.domain.model.InvoiceReceivableStatus;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public record ReceivableQueryCriteria(
    AnalyticsTenantId tenantId,
    Optional<UUID> customerId,
    Set<InvoiceReceivableStatus> statuses,
    Optional<Boolean> overdue,
    LocalDate businessDate,
    int pageNumber,
    int pageSize,
    ReceivableSortField sortField,
    SortDirection sortDirection) {

  public static final int MAX_PAGE_SIZE = 100;

  public ReceivableQueryCriteria {
    Objects.requireNonNull(tenantId, "Receivable query tenant id cannot be null");
    Objects.requireNonNull(customerId, "Receivable query customer id cannot be null");
    Objects.requireNonNull(statuses, "Receivable query statuses cannot be null");
    if (statuses.stream().anyMatch(Objects::isNull)) {
      throw new NullPointerException("Receivable query statuses cannot contain null");
    }
    statuses = Set.copyOf(statuses);
    Objects.requireNonNull(overdue, "Receivable query overdue filter cannot be null");
    Objects.requireNonNull(businessDate, "Receivable query business date cannot be null");
    if (pageNumber < 0) {
      throw new IllegalArgumentException("Receivable query page number cannot be negative");
    }
    if (pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
      throw new IllegalArgumentException(
          "Receivable query page size must be between 1 and " + MAX_PAGE_SIZE);
    }
    Objects.requireNonNull(sortField, "Receivable query sort field cannot be null");
    Objects.requireNonNull(sortDirection, "Receivable query sort direction cannot be null");
  }

  public long offset() {
    return Math.multiplyExact((long) pageNumber, pageSize);
  }
}
