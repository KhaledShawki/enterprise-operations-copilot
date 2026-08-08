package io.github.khaledshawki.eoc.platform.operations.adapter.in.web;

import io.github.khaledshawki.eoc.operations.application.exception.InvalidPaymentQueryException;
import io.github.khaledshawki.eoc.operations.application.model.authorization.OperationsActor;
import io.github.khaledshawki.eoc.operations.application.model.querying.PaymentSortField;
import io.github.khaledshawki.eoc.operations.application.model.querying.SortDirection;
import io.github.khaledshawki.eoc.operations.application.port.in.GetPaymentQuery;
import io.github.khaledshawki.eoc.operations.application.port.in.GetPaymentUseCase;
import io.github.khaledshawki.eoc.operations.application.port.in.ListPaymentsQuery;
import io.github.khaledshawki.eoc.operations.application.port.in.ListPaymentsUseCase;
import io.github.khaledshawki.eoc.operations.application.port.in.PaymentResult;
import io.github.khaledshawki.eoc.operations.domain.model.PaymentStatus;
import io.github.khaledshawki.eoc.platform.security.adapter.in.web.JwtAuthenticatedUserMapper;
import io.github.khaledshawki.eoc.platform.security.model.AuthenticatedUser;
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
    path = "/api/v1/tenants/{tenantId}/payments",
    produces = MediaType.APPLICATION_JSON_VALUE)
public class PaymentController {

  private static final String READ_PAYMENTS =
      "@tenantAccessPolicy.hasAnyRole(authentication, #p0, 'tenant-admin', "
          + "'operations-manager', 'auditor')";

  private final GetPaymentUseCase getPaymentUseCase;
  private final ListPaymentsUseCase listPaymentsUseCase;
  private final JwtAuthenticatedUserMapper jwtAuthenticatedUserMapper;

  public PaymentController(
      GetPaymentUseCase getPaymentUseCase,
      ListPaymentsUseCase listPaymentsUseCase,
      JwtAuthenticatedUserMapper jwtAuthenticatedUserMapper) {
    this.getPaymentUseCase =
        java.util.Objects.requireNonNull(getPaymentUseCase, "Get Payment use case cannot be null");
    this.listPaymentsUseCase =
        java.util.Objects.requireNonNull(
            listPaymentsUseCase, "List Payments use case cannot be null");
    this.jwtAuthenticatedUserMapper =
        java.util.Objects.requireNonNull(
            jwtAuthenticatedUserMapper, "JWT authenticated user mapper cannot be null");
  }

  @GetMapping("/{paymentId}")
  @PreAuthorize(READ_PAYMENTS)
  public PaymentResponse getPayment(
      @PathVariable UUID tenantId,
      @PathVariable UUID paymentId,
      JwtAuthenticationToken authentication) {
    PaymentResult result =
        getPaymentUseCase.get(new GetPaymentQuery(actor(authentication), tenantId, paymentId));
    return PaymentResponse.from(result);
  }

  @GetMapping
  @PreAuthorize(READ_PAYMENTS)
  public PaymentsResponse listPayments(
      @PathVariable UUID tenantId,
      @RequestParam(required = false) UUID customerId,
      @RequestParam(required = false, name = "status") Set<PaymentStatus> statuses,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate paymentDateFrom,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate paymentDateTo,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "50") int size,
      @RequestParam(defaultValue = "PAYMENT_DATE") PaymentSortField sort,
      @RequestParam(defaultValue = "DESC") SortDirection direction,
      JwtAuthenticationToken authentication) {
    ListPaymentsQuery query;
    try {
      query =
          new ListPaymentsQuery(
              actor(authentication),
              tenantId,
              Optional.ofNullable(customerId),
              statuses == null ? Set.of() : statuses,
              Optional.ofNullable(paymentDateFrom),
              Optional.ofNullable(paymentDateTo),
              page,
              size,
              sort,
              direction);
    } catch (IllegalArgumentException exception) {
      throw new InvalidPaymentQueryException(exception.getMessage(), exception);
    }
    return PaymentsResponse.from(listPaymentsUseCase.list(query));
  }

  private OperationsActor actor(JwtAuthenticationToken authentication) {
    AuthenticatedUser authenticatedUser = jwtAuthenticatedUserMapper.map(authentication);
    return new OperationsActor(authenticatedUser.issuer().toString(), authenticatedUser.subject());
  }
}
