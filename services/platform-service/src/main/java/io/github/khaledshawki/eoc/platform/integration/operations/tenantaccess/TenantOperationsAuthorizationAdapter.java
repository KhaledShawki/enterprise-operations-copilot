package io.github.khaledshawki.eoc.platform.integration.operations.tenantaccess;

import io.github.khaledshawki.eoc.operations.application.model.authorization.OperationsActor;
import io.github.khaledshawki.eoc.operations.application.model.authorization.OperationsPermission;
import io.github.khaledshawki.eoc.operations.application.port.out.OperationsAuthorizationPort;
import io.github.khaledshawki.eoc.operations.domain.model.OperationsTenantId;
import io.github.khaledshawki.eoc.tenantaccess.application.port.in.ResolveTenantAccessQuery;
import io.github.khaledshawki.eoc.tenantaccess.application.port.in.ResolveTenantAccessUseCase;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Transactional(readOnly = true)
public class TenantOperationsAuthorizationAdapter implements OperationsAuthorizationPort {

  private static final List<String> READ_INVOICE_ROLES =
      List.of("tenant-admin", "operations-manager", "auditor");

  private final ResolveTenantAccessUseCase resolveTenantAccessUseCase;

  public TenantOperationsAuthorizationAdapter(
      ResolveTenantAccessUseCase resolveTenantAccessUseCase) {
    this.resolveTenantAccessUseCase =
        Objects.requireNonNull(
            resolveTenantAccessUseCase, "Resolve tenant access use case cannot be null");
  }

  @Override
  public boolean hasPermission(
      OperationsActor actor, OperationsTenantId tenantId, OperationsPermission permission) {
    Objects.requireNonNull(actor, "Operations actor cannot be null");
    Objects.requireNonNull(tenantId, "Operations tenant id cannot be null");
    Objects.requireNonNull(permission, "Operations permission cannot be null");
    return switch (permission) {
      case READ_INVOICES ->
          READ_INVOICE_ROLES.stream().anyMatch(role -> hasRole(actor, tenantId, role));
    };
  }

  private boolean hasRole(OperationsActor actor, OperationsTenantId tenantId, String requiredRole) {
    return resolveTenantAccessUseCase
        .resolve(
            new ResolveTenantAccessQuery(
                actor.issuer(), actor.subject(), tenantId.value(), requiredRole))
        .granted();
  }
}
