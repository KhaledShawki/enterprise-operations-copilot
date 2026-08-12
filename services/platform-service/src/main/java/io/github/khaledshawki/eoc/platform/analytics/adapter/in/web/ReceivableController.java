package io.github.khaledshawki.eoc.platform.analytics.adapter.in.web;

import io.github.khaledshawki.eoc.analytics.application.exception.InvalidReceivableQueryException;
import io.github.khaledshawki.eoc.analytics.application.model.querying.ReceivableSortField;
import io.github.khaledshawki.eoc.analytics.application.model.querying.SortDirection;
import io.github.khaledshawki.eoc.analytics.application.port.in.GetReceivableQuery;
import io.github.khaledshawki.eoc.analytics.application.port.in.GetReceivableUseCase;
import io.github.khaledshawki.eoc.analytics.application.port.in.ListReceivablesQuery;
import io.github.khaledshawki.eoc.analytics.application.port.in.ListReceivablesUseCase;
import io.github.khaledshawki.eoc.analytics.domain.model.InvoiceReceivableStatus;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(
    path = "/api/v1/tenants/{tenantId}/analytics/receivables",
    produces = MediaType.APPLICATION_JSON_VALUE)
public class ReceivableController {

  private static final String READ_RECEIVABLES =
      "@tenantAccessPolicy.hasAnyRole(authentication, #p0, 'tenant-admin', "
          + "'operations-manager', 'auditor')";

  private final GetReceivableUseCase getReceivableUseCase;
  private final ListReceivablesUseCase listReceivablesUseCase;
  private final Clock clock;

  public ReceivableController(
      GetReceivableUseCase getReceivableUseCase,
      ListReceivablesUseCase listReceivablesUseCase,
      Clock clock) {
    this.getReceivableUseCase =
        Objects.requireNonNull(getReceivableUseCase, "Get Receivable use case cannot be null");
    this.listReceivablesUseCase =
        Objects.requireNonNull(listReceivablesUseCase, "List Receivables use case cannot be null");
    this.clock = Objects.requireNonNull(clock, "Clock cannot be null");
  }

  @GetMapping("/{invoiceId}")
  @PreAuthorize(READ_RECEIVABLES)
  public ReceivableResponse getReceivable(
      @PathVariable UUID tenantId,
      @PathVariable UUID invoiceId,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate businessDate) {
    return ReceivableResponse.from(
        getReceivableUseCase.get(
            new GetReceivableQuery(tenantId, invoiceId, businessDate(businessDate))));
  }

  @GetMapping
  @PreAuthorize(READ_RECEIVABLES)
  public ReceivablesResponse listReceivables(
      @PathVariable UUID tenantId,
      @RequestParam(required = false) UUID customerId,
      @RequestParam(required = false, name = "status") Set<InvoiceReceivableStatus> statuses,
      @RequestParam(required = false) Boolean overdue,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate businessDate,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "50") int size,
      @RequestParam(defaultValue = "DUE_DATE") ReceivableSortField sort,
      @RequestParam(defaultValue = "ASC") SortDirection direction) {
    try {
      return ReceivablesResponse.from(
          listReceivablesUseCase.list(
              new ListReceivablesQuery(
                  tenantId,
                  Optional.ofNullable(customerId),
                  statuses == null ? Set.of() : statuses,
                  Optional.ofNullable(overdue),
                  businessDate(businessDate),
                  page,
                  size,
                  sort,
                  direction)));
    } catch (IllegalArgumentException exception) {
      throw new InvalidReceivableQueryException(exception.getMessage(), exception);
    }
  }

  private LocalDate businessDate(LocalDate requestedBusinessDate) {
    return requestedBusinessDate == null ? LocalDate.now(clock) : requestedBusinessDate;
  }
}
