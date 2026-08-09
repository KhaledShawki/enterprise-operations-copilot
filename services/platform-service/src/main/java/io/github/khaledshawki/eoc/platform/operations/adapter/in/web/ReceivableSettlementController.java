package io.github.khaledshawki.eoc.platform.operations.adapter.in.web;

import io.github.khaledshawki.eoc.operations.application.model.authorization.OperationsActor;
import io.github.khaledshawki.eoc.operations.application.port.in.AllocateReceivablePaymentCommand;
import io.github.khaledshawki.eoc.operations.application.port.in.AllocateReceivablePaymentUseCase;
import io.github.khaledshawki.eoc.operations.application.port.in.GetReceivableSettlementQuery;
import io.github.khaledshawki.eoc.operations.application.port.in.GetReceivableSettlementUseCase;
import io.github.khaledshawki.eoc.operations.application.port.in.ReverseReceivableAllocationUseCase;
import io.github.khaledshawki.eoc.platform.security.adapter.in.web.JwtAuthenticatedUserMapper;
import io.github.khaledshawki.eoc.platform.security.model.AuthenticatedUser;
import java.util.Objects;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(
    path = "/api/v1/tenants/{tenantId}/payments/{paymentId}/receivable-settlement",
    produces = MediaType.APPLICATION_JSON_VALUE)
public class ReceivableSettlementController {

  private static final String READ_RECEIVABLE_SETTLEMENT =
      "@tenantAccessPolicy.hasAnyRole(authentication, #p0, 'tenant-admin', "
          + "'operations-manager', 'auditor')";
  private static final String MANAGE_RECEIVABLE_SETTLEMENT =
      "@tenantAccessPolicy.hasAnyRole(authentication, #p0, 'tenant-admin', "
          + "'operations-manager')";

  private final GetReceivableSettlementUseCase getReceivableSettlementUseCase;
  private final AllocateReceivablePaymentUseCase allocateReceivablePaymentUseCase;
  private final ReverseReceivableAllocationUseCase reverseReceivableAllocationUseCase;
  private final JwtAuthenticatedUserMapper jwtAuthenticatedUserMapper;

  public ReceivableSettlementController(
      GetReceivableSettlementUseCase getReceivableSettlementUseCase,
      AllocateReceivablePaymentUseCase allocateReceivablePaymentUseCase,
      ReverseReceivableAllocationUseCase reverseReceivableAllocationUseCase,
      JwtAuthenticatedUserMapper jwtAuthenticatedUserMapper) {
    this.getReceivableSettlementUseCase =
        Objects.requireNonNull(
            getReceivableSettlementUseCase, "Get receivable settlement use case cannot be null");
    this.allocateReceivablePaymentUseCase =
        Objects.requireNonNull(
            allocateReceivablePaymentUseCase,
            "Allocate receivable payment use case cannot be null");
    this.reverseReceivableAllocationUseCase =
        Objects.requireNonNull(
            reverseReceivableAllocationUseCase,
            "Reverse receivable allocation use case cannot be null");
    this.jwtAuthenticatedUserMapper =
        Objects.requireNonNull(
            jwtAuthenticatedUserMapper, "JWT authenticated user mapper cannot be null");
  }

  @GetMapping
  @PreAuthorize(READ_RECEIVABLE_SETTLEMENT)
  public ReceivableSettlementResponse getSettlement(
      @PathVariable UUID tenantId,
      @PathVariable UUID paymentId,
      JwtAuthenticationToken authentication) {
    return ReceivableSettlementResponse.from(
        getReceivableSettlementUseCase.get(
            new GetReceivableSettlementQuery(actor(authentication), tenantId, paymentId)));
  }

  @PostMapping(path = "/allocations", consumes = MediaType.APPLICATION_JSON_VALUE)
  @PreAuthorize(MANAGE_RECEIVABLE_SETTLEMENT)
  public ReceivableSettlementMutationResponse allocate(
      @PathVariable UUID tenantId,
      @PathVariable UUID paymentId,
      @RequestBody AllocateReceivablePaymentRequest request,
      JwtAuthenticationToken authentication) {
    AllocateReceivablePaymentCommand command;
    try {
      command = request.toCommand(actor(authentication), tenantId, paymentId);
    } catch (IllegalArgumentException exception) {
      throw new InvalidReceivableSettlementRequestException(exception.getMessage(), exception);
    }
    return ReceivableSettlementMutationResponse.from(
        allocateReceivablePaymentUseCase.allocate(command));
  }

  @PostMapping(
      path = "/allocations/{allocationId}/reversal",
      consumes = MediaType.APPLICATION_JSON_VALUE)
  @PreAuthorize(MANAGE_RECEIVABLE_SETTLEMENT)
  public ReceivableSettlementMutationResponse reverse(
      @PathVariable UUID tenantId,
      @PathVariable UUID paymentId,
      @PathVariable UUID allocationId,
      @RequestBody ReverseReceivableAllocationRequest request,
      JwtAuthenticationToken authentication) {
    return ReceivableSettlementMutationResponse.from(
        reverseReceivableAllocationUseCase.reverse(
            request.toCommand(actor(authentication), tenantId, paymentId, allocationId)));
  }

  private OperationsActor actor(JwtAuthenticationToken authentication) {
    AuthenticatedUser authenticatedUser = jwtAuthenticatedUserMapper.map(authentication);
    return new OperationsActor(authenticatedUser.issuer().toString(), authenticatedUser.subject());
  }
}
