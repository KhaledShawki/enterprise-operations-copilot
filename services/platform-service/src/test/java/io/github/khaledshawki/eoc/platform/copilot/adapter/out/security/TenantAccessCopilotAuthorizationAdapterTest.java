package io.github.khaledshawki.eoc.platform.copilot.adapter.out.security;

import static org.junit.jupiter.api.Assertions.*;

import io.github.khaledshawki.eoc.copilot.application.model.CopilotExecutionContext;
import io.github.khaledshawki.eoc.tenantaccess.application.port.in.*;
import java.net.URI;
import java.util.*;
import org.junit.jupiter.api.Test;

class TenantAccessCopilotAuthorizationAdapterTest {
  private static final UUID TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000111");
  private static final CopilotExecutionContext CONTEXT =
      new CopilotExecutionContext(URI.create("https://issuer.example"), "subject-1", TENANT_ID);

  @Test
  void grantsWhenAnyApprovedReceivablesRoleIsGranted() {
    List<ResolveTenantAccessQuery> queries = new ArrayList<>();
    ResolveTenantAccessUseCase useCase =
        query -> {
          queries.add(query);
          return "operations-manager".equals(query.requiredRole())
              ? ResolveTenantAccessResult.allow()
              : ResolveTenantAccessResult.deny();
        };
    var adapter = new TenantAccessCopilotAuthorizationAdapter(useCase);
    assertTrue(adapter.mayReadReceivables(CONTEXT));
    assertEquals(
        List.of("tenant-admin", "operations-manager"),
        queries.stream().map(ResolveTenantAccessQuery::requiredRole).toList());
    assertTrue(
        queries.stream()
            .allMatch(
                query ->
                    query.tenantId().equals(TENANT_ID) && query.subject().equals("subject-1")));
  }

  @Test
  void deniesWhenNoApprovedRoleIsGranted() {
    ResolveTenantAccessUseCase useCase = query -> ResolveTenantAccessResult.deny();
    assertFalse(new TenantAccessCopilotAuthorizationAdapter(useCase).mayReadReceivables(CONTEXT));
  }
}
