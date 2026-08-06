package io.github.khaledshawki.eoc.operations.application.model.querying;

import io.github.khaledshawki.eoc.operations.domain.model.BusinessPartnerId;
import io.github.khaledshawki.eoc.operations.domain.model.InvoiceStatus;
import io.github.khaledshawki.eoc.operations.domain.model.OperationsTenantId;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public record InvoiceQueryCriteria(
    OperationsTenantId tenantId,
    Optional<BusinessPartnerId> customerId,
    Set<InvoiceStatus> statuses,
    Optional<InvoiceDueState> dueState,
    LocalDate businessDate,
    int pageNumber,
    int pageSize,
    InvoiceSortField sortField,
    SortDirection sortDirection) {

  public static final int MAX_PAGE_SIZE = 100;

  public InvoiceQueryCriteria {
    Objects.requireNonNull(tenantId, "Invoice query tenant id cannot be null");
    Objects.requireNonNull(customerId, "Invoice query customer id cannot be null");
    Objects.requireNonNull(statuses, "Invoice query statuses cannot be null");
    if (statuses.stream().anyMatch(Objects::isNull)) {
      throw new NullPointerException("Invoice query statuses cannot contain null");
    }
    statuses = Set.copyOf(statuses);
    Objects.requireNonNull(dueState, "Invoice query due state cannot be null");
    Objects.requireNonNull(businessDate, "Invoice query business date cannot be null");
    if (pageNumber < 0) {
      throw new IllegalArgumentException("Invoice query page number cannot be negative");
    }
    if (pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
      throw new IllegalArgumentException(
          "Invoice query page size must be between 1 and " + MAX_PAGE_SIZE);
    }
    Objects.requireNonNull(sortField, "Invoice query sort field cannot be null");
    Objects.requireNonNull(sortDirection, "Invoice query sort direction cannot be null");
  }
}
