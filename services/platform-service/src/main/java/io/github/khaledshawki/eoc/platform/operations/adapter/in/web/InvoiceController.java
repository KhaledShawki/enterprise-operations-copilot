package io.github.khaledshawki.eoc.platform.operations.adapter.in.web;

import io.github.khaledshawki.eoc.operations.application.exception.InvalidInvoiceQueryException;
import io.github.khaledshawki.eoc.operations.application.model.authorization.OperationsActor;
import io.github.khaledshawki.eoc.operations.application.model.querying.InvoiceDueState;
import io.github.khaledshawki.eoc.operations.application.model.querying.InvoiceSortField;
import io.github.khaledshawki.eoc.operations.application.model.querying.SortDirection;
import io.github.khaledshawki.eoc.operations.application.port.in.GetInvoiceQuery;
import io.github.khaledshawki.eoc.operations.application.port.in.GetInvoiceUseCase;
import io.github.khaledshawki.eoc.operations.application.port.in.InvoiceResult;
import io.github.khaledshawki.eoc.operations.application.port.in.ListInvoicesQuery;
import io.github.khaledshawki.eoc.operations.application.port.in.ListInvoicesUseCase;
import io.github.khaledshawki.eoc.operations.domain.model.InvoiceStatus;
import io.github.khaledshawki.eoc.platform.security.adapter.in.web.JwtAuthenticatedUserMapper;
import io.github.khaledshawki.eoc.platform.security.model.AuthenticatedUser;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(
    path = "/api/v1/tenants/{tenantId}/invoices",
    produces = MediaType.APPLICATION_JSON_VALUE)
public class InvoiceController {

  private static final String READ_INVOICES =
      "@tenantAccessPolicy.hasAnyRole(authentication, #p0, 'tenant-admin', "
          + "'operations-manager', 'auditor')";

  private final GetInvoiceUseCase getInvoiceUseCase;
  private final ListInvoicesUseCase listInvoicesUseCase;
  private final JwtAuthenticatedUserMapper jwtAuthenticatedUserMapper;
  private final Clock clock;

  public InvoiceController(
      GetInvoiceUseCase getInvoiceUseCase,
      ListInvoicesUseCase listInvoicesUseCase,
      JwtAuthenticatedUserMapper jwtAuthenticatedUserMapper,
      Clock clock) {
    this.getInvoiceUseCase =
        java.util.Objects.requireNonNull(getInvoiceUseCase, "Get Invoice use case cannot be null");
    this.listInvoicesUseCase =
        java.util.Objects.requireNonNull(
            listInvoicesUseCase, "List Invoices use case cannot be null");
    this.jwtAuthenticatedUserMapper =
        java.util.Objects.requireNonNull(
            jwtAuthenticatedUserMapper, "JWT authenticated user mapper cannot be null");
    this.clock = java.util.Objects.requireNonNull(clock, "Clock cannot be null");
  }

  @GetMapping("/{invoiceId}")
  @PreAuthorize(READ_INVOICES)
  public InvoiceResponse getInvoice(
      @PathVariable UUID tenantId,
      @PathVariable UUID invoiceId,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate businessDate,
      JwtAuthenticationToken authentication) {
    InvoiceResult result =
        getInvoiceUseCase.get(
            new GetInvoiceQuery(
                actor(authentication), tenantId, invoiceId, businessDate(businessDate)));
    return InvoiceResponse.from(result);
  }

  @GetMapping
  @PreAuthorize(READ_INVOICES)
  public InvoicesResponse listInvoices(
      @PathVariable UUID tenantId,
      @RequestParam(required = false) UUID customerId,
      @RequestParam(required = false, name = "status") Set<InvoiceStatus> statuses,
      @RequestParam(required = false) InvoiceDueState dueState,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate businessDate,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "50") int size,
      @RequestParam(defaultValue = "ISSUE_DATE") InvoiceSortField sort,
      @RequestParam(defaultValue = "DESC") SortDirection direction,
      JwtAuthenticationToken authentication) {
    ListInvoicesQuery query;
    try {
      query =
          new ListInvoicesQuery(
              actor(authentication),
              tenantId,
              Optional.ofNullable(customerId),
              statuses == null ? Set.of() : statuses,
              Optional.ofNullable(dueState),
              businessDate(businessDate),
              page,
              size,
              sort,
              direction);
    } catch (IllegalArgumentException exception) {
      throw new InvalidInvoiceQueryException(exception.getMessage(), exception);
    }
    return InvoicesResponse.from(listInvoicesUseCase.list(query));
  }

  private LocalDate businessDate(LocalDate requestedBusinessDate) {
    return requestedBusinessDate == null ? LocalDate.now(clock) : requestedBusinessDate;
  }

  private OperationsActor actor(JwtAuthenticationToken authentication) {
    AuthenticatedUser authenticatedUser = jwtAuthenticatedUserMapper.map(authentication);
    return new OperationsActor(authenticatedUser.issuer().toString(), authenticatedUser.subject());
  }
}
