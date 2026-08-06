package io.github.khaledshawki.eoc.platform.integration.operations.tenantaccess;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.khaledshawki.eoc.operations.application.model.authorization.OperationsActor;
import io.github.khaledshawki.eoc.operations.application.model.authorization.OperationsPermission;
import io.github.khaledshawki.eoc.operations.domain.model.OperationsTenantId;
import io.github.khaledshawki.eoc.tenantaccess.application.port.in.ResolveTenantAccessResult;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TenantOperationsAuthorizationAdapterTest {

  private static final OperationsActor ACTOR = new OperationsActor("issuer", "subject");
  private static final OperationsTenantId TENANT_ID =
      OperationsTenantId.of(UUID.fromString("00000000-0000-0000-0000-000000000001"));

  @Test
  void shouldGrantReadInvoicesToEveryApprovedTenantRole() {
    for (String role : Set.of("tenant-admin", "operations-manager", "auditor")) {
      TenantOperationsAuthorizationAdapter adapter =
          new TenantOperationsAuthorizationAdapter(
              query ->
                  query.requiredRole().equals(role)
                      ? ResolveTenantAccessResult.allow()
                      : ResolveTenantAccessResult.deny());

      assertTrue(adapter.hasPermission(ACTOR, TENANT_ID, OperationsPermission.READ_INVOICES));
    }
  }

  @Test
  void shouldDenyReadInvoicesWhenNoApprovedRoleIsGranted() {
    TenantOperationsAuthorizationAdapter adapter =
        new TenantOperationsAuthorizationAdapter(query -> ResolveTenantAccessResult.deny());

    assertFalse(adapter.hasPermission(ACTOR, TENANT_ID, OperationsPermission.READ_INVOICES));
  }
}
