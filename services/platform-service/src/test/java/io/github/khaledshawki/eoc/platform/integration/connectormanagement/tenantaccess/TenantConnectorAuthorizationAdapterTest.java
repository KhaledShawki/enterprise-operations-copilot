package io.github.khaledshawki.eoc.platform.integration.connectormanagement.tenantaccess;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.khaledshawki.eoc.connectormanagement.application.model.authorization.ConnectorActor;
import io.github.khaledshawki.eoc.connectormanagement.application.model.authorization.ConnectorPermission;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ConnectorTenantId;
import io.github.khaledshawki.eoc.tenantaccess.application.port.in.ResolveTenantAccessQuery;
import io.github.khaledshawki.eoc.tenantaccess.application.port.in.ResolveTenantAccessResult;
import io.github.khaledshawki.eoc.tenantaccess.application.port.in.ResolveTenantAccessUseCase;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TenantConnectorAuthorizationAdapterTest {

  private static final ConnectorActor ACTOR =
      new ConnectorActor("https://identity.example.com/realms/eoc", "connector-user");
  private static final ConnectorTenantId TENANT_ID =
      ConnectorTenantId.of(UUID.fromString("00000000-0000-0000-0000-000000000010"));

  @Test
  void shouldGrantAdministrationOnlyForTenantAdmin() {
    RecordingResolveTenantAccessUseCase granted =
        new RecordingResolveTenantAccessUseCase(Set.of("tenant-admin"));
    RecordingResolveTenantAccessUseCase denied =
        new RecordingResolveTenantAccessUseCase(Set.of("operations-manager", "auditor"));

    assertTrue(adapter(granted).hasPermission(ACTOR, TENANT_ID, ConnectorPermission.ADMINISTER));
    assertFalse(adapter(denied).hasPermission(ACTOR, TENANT_ID, ConnectorPermission.ADMINISTER));

    assertEquals(List.of("tenant-admin"), granted.requestedRoles());
    assertEquals(List.of("tenant-admin"), denied.requestedRoles());
  }

  @Test
  void shouldGrantImportExecutionForTenantAdmin() {
    RecordingResolveTenantAccessUseCase resolver =
        new RecordingResolveTenantAccessUseCase(Set.of("tenant-admin"));

    assertTrue(
        adapter(resolver).hasPermission(ACTOR, TENANT_ID, ConnectorPermission.EXECUTE_IMPORT));
    assertEquals(List.of("tenant-admin"), resolver.requestedRoles());
  }

  @Test
  void shouldGrantImportExecutionForOperationsManagerAfterTenantAdminDenial() {
    RecordingResolveTenantAccessUseCase resolver =
        new RecordingResolveTenantAccessUseCase(Set.of("operations-manager"));

    assertTrue(
        adapter(resolver).hasPermission(ACTOR, TENANT_ID, ConnectorPermission.EXECUTE_IMPORT));
    assertEquals(List.of("tenant-admin", "operations-manager"), resolver.requestedRoles());
  }

  @Test
  void shouldDenyImportExecutionForAuditor() {
    RecordingResolveTenantAccessUseCase resolver =
        new RecordingResolveTenantAccessUseCase(Set.of("auditor"));

    assertFalse(
        adapter(resolver).hasPermission(ACTOR, TENANT_ID, ConnectorPermission.EXECUTE_IMPORT));
    assertEquals(List.of("tenant-admin", "operations-manager"), resolver.requestedRoles());
  }

  @Test
  void shouldGrantReadForTenantAdmin() {
    RecordingResolveTenantAccessUseCase resolver =
        new RecordingResolveTenantAccessUseCase(Set.of("tenant-admin"));

    assertTrue(adapter(resolver).hasPermission(ACTOR, TENANT_ID, ConnectorPermission.READ));
    assertEquals(List.of("tenant-admin"), resolver.requestedRoles());
  }

  @Test
  void shouldGrantReadForOperationsManagerAfterTenantAdminDenial() {
    RecordingResolveTenantAccessUseCase resolver =
        new RecordingResolveTenantAccessUseCase(Set.of("operations-manager"));

    assertTrue(adapter(resolver).hasPermission(ACTOR, TENANT_ID, ConnectorPermission.READ));
    assertEquals(List.of("tenant-admin", "operations-manager"), resolver.requestedRoles());
  }

  @Test
  void shouldGrantReadForAuditorAfterEarlierRoleDenials() {
    RecordingResolveTenantAccessUseCase resolver =
        new RecordingResolveTenantAccessUseCase(Set.of("auditor"));

    assertTrue(adapter(resolver).hasPermission(ACTOR, TENANT_ID, ConnectorPermission.READ));
    assertEquals(
        List.of("tenant-admin", "operations-manager", "auditor"), resolver.requestedRoles());
  }

  @Test
  void shouldDenyReadWhenNoSupportedTenantRoleIsGranted() {
    RecordingResolveTenantAccessUseCase resolver =
        new RecordingResolveTenantAccessUseCase(Set.of("unrelated-role"));

    assertFalse(adapter(resolver).hasPermission(ACTOR, TENANT_ID, ConnectorPermission.READ));
    assertEquals(
        List.of("tenant-admin", "operations-manager", "auditor"), resolver.requestedRoles());
  }

  @Test
  void shouldForwardExactExternalIdentityAndTenant() {
    RecordingResolveTenantAccessUseCase resolver =
        new RecordingResolveTenantAccessUseCase(Set.of("tenant-admin"));

    adapter(resolver).hasPermission(ACTOR, TENANT_ID, ConnectorPermission.ADMINISTER);

    ResolveTenantAccessQuery query = resolver.queries().getFirst();
    assertEquals(ACTOR.issuer(), query.issuer());
    assertEquals(ACTOR.subject(), query.subject());
    assertEquals(TENANT_ID.value(), query.tenantId());
    assertEquals("tenant-admin", query.requiredRole());
  }

  @Test
  void shouldRejectNullDependenciesAndArguments() {
    assertThrows(NullPointerException.class, () -> new TenantConnectorAuthorizationAdapter(null));

    TenantConnectorAuthorizationAdapter adapter =
        adapter(new RecordingResolveTenantAccessUseCase(Set.of()));

    assertThrows(
        NullPointerException.class,
        () -> adapter.hasPermission(null, TENANT_ID, ConnectorPermission.READ));
    assertThrows(
        NullPointerException.class,
        () -> adapter.hasPermission(ACTOR, null, ConnectorPermission.READ));
    assertThrows(NullPointerException.class, () -> adapter.hasPermission(ACTOR, TENANT_ID, null));
  }

  private static TenantConnectorAuthorizationAdapter adapter(
      ResolveTenantAccessUseCase resolveTenantAccessUseCase) {
    return new TenantConnectorAuthorizationAdapter(resolveTenantAccessUseCase);
  }

  private static final class RecordingResolveTenantAccessUseCase
      implements ResolveTenantAccessUseCase {

    private final Set<String> grantedRoles;
    private final List<ResolveTenantAccessQuery> queries = new ArrayList<>();

    private RecordingResolveTenantAccessUseCase(Set<String> grantedRoles) {
      this.grantedRoles = new HashSet<>(grantedRoles);
    }

    @Override
    public ResolveTenantAccessResult resolve(ResolveTenantAccessQuery query) {
      queries.add(query);
      return grantedRoles.contains(query.requiredRole())
          ? ResolveTenantAccessResult.allow()
          : ResolveTenantAccessResult.deny();
    }

    List<ResolveTenantAccessQuery> queries() {
      return List.copyOf(queries);
    }

    List<String> requestedRoles() {
      return queries.stream().map(ResolveTenantAccessQuery::requiredRole).toList();
    }
  }
}
