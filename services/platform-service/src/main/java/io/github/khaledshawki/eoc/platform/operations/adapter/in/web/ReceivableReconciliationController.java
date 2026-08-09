package io.github.khaledshawki.eoc.platform.operations.adapter.in.web;

import io.github.khaledshawki.eoc.operations.application.model.authorization.OperationsActor;
import io.github.khaledshawki.eoc.operations.application.port.in.GetReceivableReconciliationQuery;
import io.github.khaledshawki.eoc.operations.application.port.in.GetReceivableReconciliationUseCase;
import io.github.khaledshawki.eoc.platform.security.adapter.in.web.JwtAuthenticatedUserMapper;
import io.github.khaledshawki.eoc.platform.security.model.AuthenticatedUser;
import java.util.Objects;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(
    path = "/api/v1/tenants/{tenantId}/invoices/{invoiceId}/receivable-reconciliation",
    produces = MediaType.APPLICATION_JSON_VALUE)
public class ReceivableReconciliationController {

  private static final String READ_RECEIVABLE_RECONCILIATION =
      "@tenantAccessPolicy.hasAnyRole(authentication, #p0, 'tenant-admin', "
          + "'operations-manager', 'auditor')";

  private final GetReceivableReconciliationUseCase getReceivableReconciliationUseCase;
  private final JwtAuthenticatedUserMapper jwtAuthenticatedUserMapper;

  public ReceivableReconciliationController(
      GetReceivableReconciliationUseCase getReceivableReconciliationUseCase,
      JwtAuthenticatedUserMapper jwtAuthenticatedUserMapper) {
    this.getReceivableReconciliationUseCase =
        Objects.requireNonNull(
            getReceivableReconciliationUseCase,
            "Get receivable reconciliation use case cannot be null");
    this.jwtAuthenticatedUserMapper =
        Objects.requireNonNull(
            jwtAuthenticatedUserMapper, "JWT authenticated user mapper cannot be null");
  }

  @GetMapping
  @PreAuthorize(READ_RECEIVABLE_RECONCILIATION)
  public ReceivableReconciliationResponse getReconciliation(
      @PathVariable UUID tenantId,
      @PathVariable UUID invoiceId,
      JwtAuthenticationToken authentication) {
    return ReceivableReconciliationResponse.from(
        getReceivableReconciliationUseCase.get(
            new GetReceivableReconciliationQuery(actor(authentication), tenantId, invoiceId)));
  }

  private OperationsActor actor(JwtAuthenticationToken authentication) {
    AuthenticatedUser authenticatedUser = jwtAuthenticatedUserMapper.map(authentication);
    return new OperationsActor(authenticatedUser.issuer().toString(), authenticatedUser.subject());
  }
}
