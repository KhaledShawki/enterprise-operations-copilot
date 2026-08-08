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
  void shouldGrantOperationsReadsToEveryApprovedTenantRole() {
    for (String role : Set.of("tenant-admin", "operations-manager", "auditor")) {
      TenantOperationsAuthorizationAdapter adapter = adapterGranting(role);

      assertTrue(adapter.hasPermission(ACTOR, TENANT_ID, OperationsPermission.READ_INVOICES));
      assertTrue(adapter.hasPermission(ACTOR, TENANT_ID, OperationsPermission.READ_PAYMENTS));
    }
  }

  @Test
  void shouldGrantReceivableSettlementManagementOnlyToApprovedWriteRoles() {
    for (String role : Set.of("tenant-admin", "operations-manager")) {
      assertTrue(
          adapterGranting(role)
              .hasPermission(ACTOR, TENANT_ID, OperationsPermission.MANAGE_RECEIVABLE_SETTLEMENTS));
    }
    assertFalse(
        adapterGranting("auditor")
            .hasPermission(ACTOR, TENANT_ID, OperationsPermission.MANAGE_RECEIVABLE_SETTLEMENTS));
  }

  @Test
  void shouldDenyOperationsPermissionsWhenNoApprovedRoleIsGranted() {
    TenantOperationsAuthorizationAdapter adapter =
        new TenantOperationsAuthorizationAdapter(query -> ResolveTenantAccessResult.deny());

    assertFalse(adapter.hasPermission(ACTOR, TENANT_ID, OperationsPermission.READ_INVOICES));
    assertFalse(adapter.hasPermission(ACTOR, TENANT_ID, OperationsPermission.READ_PAYMENTS));
    assertFalse(
        adapter.hasPermission(
            ACTOR, TENANT_ID, OperationsPermission.MANAGE_RECEIVABLE_SETTLEMENTS));
  }

  private static TenantOperationsAuthorizationAdapter adapterGranting(String grantedRole) {
    return new TenantOperationsAuthorizationAdapter(
        query ->
            query.requiredRole().equals(grantedRole)
                ? ResolveTenantAccessResult.allow()
                : ResolveTenantAccessResult.deny());
  }
}
