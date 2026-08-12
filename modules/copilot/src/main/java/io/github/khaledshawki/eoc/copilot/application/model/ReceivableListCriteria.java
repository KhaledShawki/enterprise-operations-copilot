package io.github.khaledshawki.eoc.copilot.application.model;

import java.time.LocalDate;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public record ReceivableListCriteria(
    Optional<UUID> customerId,
    Set<ReceivableStatus> statuses,
    Optional<Boolean> overdue,
    LocalDate businessDate,
    int pageNumber,
    int pageSize,
    ReceivableSortField sortField,
    SortDirection sortDirection) {

  public static ReceivableListCriteria from(
      ListReceivablesToolRequest request, LocalDate businessDate) {
    return new ReceivableListCriteria(
        request.customerId(),
        request.statuses(),
        request.overdue(),
        businessDate,
        request.pageNumber(),
        request.pageSize(),
        request.sortField(),
        request.sortDirection());
  }
}
