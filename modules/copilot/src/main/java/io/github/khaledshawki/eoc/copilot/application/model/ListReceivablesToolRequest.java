package io.github.khaledshawki.eoc.copilot.application.model;

import io.github.khaledshawki.eoc.copilot.application.exception.InvalidCopilotToolArgumentsException;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public record ListReceivablesToolRequest(
    Optional<UUID> customerId,
    Set<ReceivableStatus> statuses,
    Optional<Boolean> overdue,
    Optional<LocalDate> businessDate,
    int pageNumber,
    int pageSize,
    ReceivableSortField sortField,
    SortDirection sortDirection) {

  public static final int MAX_PAGE_NUMBER = 1_000;
  public static final int MAX_PAGE_SIZE = 25;

  public ListReceivablesToolRequest {
    if (customerId == null || statuses == null || overdue == null || businessDate == null) {
      throw new InvalidCopilotToolArgumentsException(
          "Optional tool arguments must use non-null containers");
    }
    if (statuses.stream().anyMatch(Objects::isNull)) {
      throw new InvalidCopilotToolArgumentsException("statuses cannot contain null values");
    }
    statuses = Set.copyOf(statuses);
    if (pageNumber < 0 || pageNumber > MAX_PAGE_NUMBER) {
      throw new InvalidCopilotToolArgumentsException(
          "pageNumber must be between 0 and " + MAX_PAGE_NUMBER);
    }
    if (pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
      throw new InvalidCopilotToolArgumentsException(
          "pageSize must be between 1 and " + MAX_PAGE_SIZE);
    }
    if (sortField == null) {
      throw new InvalidCopilotToolArgumentsException("sortField is required");
    }
    if (sortDirection == null) {
      throw new InvalidCopilotToolArgumentsException("sortDirection is required");
    }
  }

  public CopilotToolName toolName() {
    return CopilotToolName.LIST_RECEIVABLES;
  }
}
