package io.github.khaledshawki.eoc.platform.integration.connectormanagement.tenantaccess;

import io.github.khaledshawki.eoc.connectormanagement.application.model.authorization.ConnectorActor;
import io.github.khaledshawki.eoc.connectormanagement.application.model.authorization.ConnectorPermission;
import io.github.khaledshawki.eoc.connectormanagement.application.port.out.ConnectorAuthorizationPort;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ConnectorTenantId;
import io.github.khaledshawki.eoc.tenantaccess.application.port.in.ResolveTenantAccessQuery;
import io.github.khaledshawki.eoc.tenantaccess.application.port.in.ResolveTenantAccessUseCase;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Transactional(readOnly = true)
public class TenantConnectorAuthorizationAdapter implements ConnectorAuthorizationPort {

  private static final String TENANT_ADMIN = "tenant-admin";
  private static final List<String> READ_ROLES =
      List.of(TENANT_ADMIN, "operations-manager", "auditor");

  private final ResolveTenantAccessUseCase resolveTenantAccessUseCase;

  public TenantConnectorAuthorizationAdapter(
      ResolveTenantAccessUseCase resolveTenantAccessUseCase) {
    this.resolveTenantAccessUseCase =
        Objects.requireNonNull(
            resolveTenantAccessUseCase, "Resolve tenant access use case cannot be null");
  }

  @Override
  public boolean hasPermission(
      ConnectorActor actor, ConnectorTenantId tenantId, ConnectorPermission permission) {
    Objects.requireNonNull(actor, "Connector actor cannot be null");
    Objects.requireNonNull(tenantId, "Connector tenant id cannot be null");
    Objects.requireNonNull(permission, "Connector permission cannot be null");

    return switch (permission) {
      case ADMINISTER -> hasRole(actor, tenantId, TENANT_ADMIN);
      case READ -> READ_ROLES.stream().anyMatch(role -> hasRole(actor, tenantId, role));
    };
  }

  private boolean hasRole(ConnectorActor actor, ConnectorTenantId tenantId, String requiredRole) {
    return resolveTenantAccessUseCase
        .resolve(
            new ResolveTenantAccessQuery(
                actor.issuer(), actor.subject(), tenantId.value(), requiredRole))
        .granted();
  }
}
