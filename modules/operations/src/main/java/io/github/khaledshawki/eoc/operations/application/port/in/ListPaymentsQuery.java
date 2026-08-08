package io.github.khaledshawki.eoc.operations.application.port.in;

import io.github.khaledshawki.eoc.operations.application.model.authorization.OperationsActor;
import io.github.khaledshawki.eoc.operations.application.model.querying.PaymentQueryCriteria;
import io.github.khaledshawki.eoc.operations.application.model.querying.PaymentSortField;
import io.github.khaledshawki.eoc.operations.application.model.querying.SortDirection;
import io.github.khaledshawki.eoc.operations.domain.model.BusinessPartnerId;
import io.github.khaledshawki.eoc.operations.domain.model.OperationsTenantId;
import io.github.khaledshawki.eoc.operations.domain.model.PaymentStatus;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public record ListPaymentsQuery(
    OperationsActor actor,
    UUID tenantId,
    Optional<UUID> customerId,
    Set<PaymentStatus> statuses,
    Optional<LocalDate> paymentDateFrom,
    Optional<LocalDate> paymentDateTo,
    int pageNumber,
    int pageSize,
    PaymentSortField sortField,
    SortDirection sortDirection) {

  public ListPaymentsQuery {
    Objects.requireNonNull(actor, "Operations actor cannot be null");
    Objects.requireNonNull(tenantId, "Payment tenant id cannot be null");
    Objects.requireNonNull(customerId, "Payment customer id cannot be null");
    Objects.requireNonNull(statuses, "Payment statuses cannot be null");
    if (statuses.stream().anyMatch(Objects::isNull)) {
      throw new NullPointerException("Payment statuses cannot contain null");
    }
    statuses = Set.copyOf(statuses);
    Objects.requireNonNull(paymentDateFrom, "Payment start date cannot be null");
    Objects.requireNonNull(paymentDateTo, "Payment end date cannot be null");
    Objects.requireNonNull(sortField, "Payment sort field cannot be null");
    Objects.requireNonNull(sortDirection, "Payment sort direction cannot be null");
    new PaymentQueryCriteria(
        OperationsTenantId.of(tenantId),
        customerId.map(BusinessPartnerId::of),
        statuses,
        paymentDateFrom,
        paymentDateTo,
        pageNumber,
        pageSize,
        sortField,
        sortDirection);
  }

  public PaymentQueryCriteria criteria() {
    return new PaymentQueryCriteria(
        OperationsTenantId.of(tenantId),
        customerId.map(BusinessPartnerId::of),
        statuses,
        paymentDateFrom,
        paymentDateTo,
        pageNumber,
        pageSize,
        sortField,
        sortDirection);
  }
}
