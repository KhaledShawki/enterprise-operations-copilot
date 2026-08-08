package io.github.khaledshawki.eoc.operations.application.model.querying;

import io.github.khaledshawki.eoc.operations.domain.model.BusinessPartnerId;
import io.github.khaledshawki.eoc.operations.domain.model.OperationsTenantId;
import io.github.khaledshawki.eoc.operations.domain.model.PaymentStatus;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public record PaymentQueryCriteria(
    OperationsTenantId tenantId,
    Optional<BusinessPartnerId> customerId,
    Set<PaymentStatus> statuses,
    Optional<LocalDate> paymentDateFrom,
    Optional<LocalDate> paymentDateTo,
    int pageNumber,
    int pageSize,
    PaymentSortField sortField,
    SortDirection sortDirection) {

  public static final int MAX_PAGE_SIZE = 100;

  public PaymentQueryCriteria {
    Objects.requireNonNull(tenantId, "Payment query tenant id cannot be null");
    Objects.requireNonNull(customerId, "Payment query customer id cannot be null");
    Objects.requireNonNull(statuses, "Payment query statuses cannot be null");
    if (statuses.stream().anyMatch(Objects::isNull)) {
      throw new NullPointerException("Payment query statuses cannot contain null");
    }
    statuses = Set.copyOf(statuses);
    Objects.requireNonNull(paymentDateFrom, "Payment query start date cannot be null");
    Objects.requireNonNull(paymentDateTo, "Payment query end date cannot be null");
    if (paymentDateFrom.isPresent()
        && paymentDateTo.isPresent()
        && paymentDateFrom.orElseThrow().isAfter(paymentDateTo.orElseThrow())) {
      throw new IllegalArgumentException("Payment query start date cannot be after end date");
    }
    if (pageNumber < 0) {
      throw new IllegalArgumentException("Payment query page number cannot be negative");
    }
    if (pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
      throw new IllegalArgumentException(
          "Payment query page size must be between 1 and " + MAX_PAGE_SIZE);
    }
    Objects.requireNonNull(sortField, "Payment query sort field cannot be null");
    Objects.requireNonNull(sortDirection, "Payment query sort direction cannot be null");
  }
}
