package io.github.khaledshawki.eoc.analytics.application.port.in;

import io.github.khaledshawki.eoc.analytics.application.model.querying.ReceivableQueryCriteria;
import io.github.khaledshawki.eoc.analytics.application.model.querying.ReceivableSortField;
import io.github.khaledshawki.eoc.analytics.application.model.querying.SortDirection;
import io.github.khaledshawki.eoc.analytics.domain.model.AnalyticsTenantId;
import io.github.khaledshawki.eoc.analytics.domain.model.InvoiceReceivableStatus;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public record ListReceivablesQuery(
    UUID tenantId,
    Optional<UUID> customerId,
    Set<InvoiceReceivableStatus> statuses,
    Optional<Boolean> overdue,
    LocalDate businessDate,
    int pageNumber,
    int pageSize,
    ReceivableSortField sortField,
    SortDirection sortDirection) {

  public ListReceivablesQuery {
    Objects.requireNonNull(tenantId, "Receivable tenant id cannot be null");
    Objects.requireNonNull(customerId, "Receivable customer id cannot be null");
    Objects.requireNonNull(statuses, "Receivable statuses cannot be null");
    if (statuses.stream().anyMatch(Objects::isNull)) {
      throw new NullPointerException("Receivable statuses cannot contain null");
    }
    statuses = Set.copyOf(statuses);
    Objects.requireNonNull(overdue, "Receivable overdue filter cannot be null");
    Objects.requireNonNull(businessDate, "Receivable business date cannot be null");
    Objects.requireNonNull(sortField, "Receivable sort field cannot be null");
    Objects.requireNonNull(sortDirection, "Receivable sort direction cannot be null");
    new ReceivableQueryCriteria(
        AnalyticsTenantId.of(tenantId),
        customerId,
        statuses,
        overdue,
        businessDate,
        pageNumber,
        pageSize,
        sortField,
        sortDirection);
  }

  public ReceivableQueryCriteria criteria() {
    return new ReceivableQueryCriteria(
        AnalyticsTenantId.of(tenantId),
        customerId,
        statuses,
        overdue,
        businessDate,
        pageNumber,
        pageSize,
        sortField,
        sortDirection);
  }
}
