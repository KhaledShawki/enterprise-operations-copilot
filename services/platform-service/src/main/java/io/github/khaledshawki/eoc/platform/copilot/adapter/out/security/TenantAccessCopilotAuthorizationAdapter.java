package io.github.khaledshawki.eoc.platform.copilot.adapter.out.security;

import io.github.khaledshawki.eoc.copilot.application.model.CopilotExecutionContext;
import io.github.khaledshawki.eoc.copilot.application.port.out.CopilotReceivablesAuthorizationPort;
import io.github.khaledshawki.eoc.tenantaccess.application.port.in.ResolveTenantAccessQuery;
import io.github.khaledshawki.eoc.tenantaccess.application.port.in.ResolveTenantAccessUseCase;
import java.util.List;
import java.util.Objects;
import org.springframework.transaction.annotation.Transactional;

public class TenantAccessCopilotAuthorizationAdapter
    implements CopilotReceivablesAuthorizationPort {
  private static final List<String> RECEIVABLES_READ_ROLES =
      List.of("tenant-admin", "operations-manager", "auditor");

  private final ResolveTenantAccessUseCase resolveTenantAccessUseCase;

  public TenantAccessCopilotAuthorizationAdapter(
      ResolveTenantAccessUseCase resolveTenantAccessUseCase) {
    this.resolveTenantAccessUseCase =
        Objects.requireNonNull(
            resolveTenantAccessUseCase, "Resolve tenant access use case cannot be null");
  }

  @Override
  @Transactional(readOnly = true)
  public boolean mayReadReceivables(CopilotExecutionContext context) {
    Objects.requireNonNull(context, "Copilot execution context cannot be null");
    for (String role : RECEIVABLES_READ_ROLES) {
      if (resolveTenantAccessUseCase
          .resolve(
              new ResolveTenantAccessQuery(
                  context.issuer().toString(), context.subject(), context.tenantId(), role))
          .granted()) {
        return true;
      }
    }
    return false;
  }
}
