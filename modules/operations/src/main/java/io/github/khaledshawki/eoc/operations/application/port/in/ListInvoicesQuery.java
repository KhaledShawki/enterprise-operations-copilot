package io.github.khaledshawki.eoc.operations.application.port.in;

import io.github.khaledshawki.eoc.operations.application.model.authorization.OperationsActor;
import io.github.khaledshawki.eoc.operations.application.model.querying.InvoiceDueState;
import io.github.khaledshawki.eoc.operations.application.model.querying.InvoiceQueryCriteria;
import io.github.khaledshawki.eoc.operations.application.model.querying.InvoiceSortField;
import io.github.khaledshawki.eoc.operations.application.model.querying.SortDirection;
import io.github.khaledshawki.eoc.operations.domain.model.BusinessPartnerId;
import io.github.khaledshawki.eoc.operations.domain.model.InvoiceStatus;
import io.github.khaledshawki.eoc.operations.domain.model.OperationsTenantId;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public record ListInvoicesQuery(
    OperationsActor actor,
    UUID tenantId,
    Optional<UUID> customerId,
    Set<InvoiceStatus> statuses,
    Optional<InvoiceDueState> dueState,
    LocalDate businessDate,
    int pageNumber,
    int pageSize,
    InvoiceSortField sortField,
    SortDirection sortDirection) {

  public ListInvoicesQuery {
    Objects.requireNonNull(actor, "Operations actor cannot be null");
    Objects.requireNonNull(tenantId, "Invoice tenant id cannot be null");
    Objects.requireNonNull(customerId, "Invoice customer id cannot be null");
    Objects.requireNonNull(statuses, "Invoice statuses cannot be null");
    if (statuses.stream().anyMatch(Objects::isNull)) {
      throw new NullPointerException("Invoice statuses cannot contain null");
    }
    statuses = Set.copyOf(statuses);
    Objects.requireNonNull(dueState, "Invoice due state cannot be null");
    Objects.requireNonNull(businessDate, "Invoice business date cannot be null");
    Objects.requireNonNull(sortField, "Invoice sort field cannot be null");
    Objects.requireNonNull(sortDirection, "Invoice sort direction cannot be null");
    new InvoiceQueryCriteria(
        OperationsTenantId.of(tenantId),
        customerId.map(BusinessPartnerId::of),
        statuses,
        dueState,
        businessDate,
        pageNumber,
        pageSize,
        sortField,
        sortDirection);
  }

  public InvoiceQueryCriteria criteria() {
    return new InvoiceQueryCriteria(
        OperationsTenantId.of(tenantId),
        customerId.map(BusinessPartnerId::of),
        statuses,
        dueState,
        businessDate,
        pageNumber,
        pageSize,
        sortField,
        sortDirection);
  }
}
