package io.github.khaledshawki.eoc.platform.integration.connectormanagement.tenantaccess;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.khaledshawki.eoc.connectormanagement.application.exception.ConnectorAccessDeniedException;
import io.github.khaledshawki.eoc.connectormanagement.application.model.authorization.ConnectorActor;
import io.github.khaledshawki.eoc.connectormanagement.application.port.in.ExecuteImportRunCommand;
import io.github.khaledshawki.eoc.connectormanagement.application.port.in.ExecuteImportRunUseCase;
import io.github.khaledshawki.eoc.connectormanagement.application.port.in.ImportRunLifecycleUseCase;
import io.github.khaledshawki.eoc.connectormanagement.application.port.in.ImportRunReference;
import io.github.khaledshawki.eoc.connectormanagement.application.port.in.ImportRunResult;
import io.github.khaledshawki.eoc.connectormanagement.application.port.in.RequestImportRunCommand;
import io.github.khaledshawki.eoc.connectormanagement.application.port.out.ConnectorRepository;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.Connector;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ConnectorEndpoint;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ConnectorName;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ConnectorTenantId;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ConnectorType;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.CredentialReference;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ImportMode;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ImportStatistics;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ImportStatus;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ImportType;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.SyncPolicy;
import io.github.khaledshawki.eoc.platform.TestcontainersConfiguration;
import io.github.khaledshawki.eoc.tenantaccess.application.port.out.PlatformUserRepository;
import io.github.khaledshawki.eoc.tenantaccess.application.port.out.TenantMembershipRepository;
import io.github.khaledshawki.eoc.tenantaccess.application.port.out.TenantRepository;
import io.github.khaledshawki.eoc.tenantaccess.domain.model.ExternalIdentity;
import io.github.khaledshawki.eoc.tenantaccess.domain.model.PlatformUser;
import io.github.khaledshawki.eoc.tenantaccess.domain.model.Tenant;
import io.github.khaledshawki.eoc.tenantaccess.domain.model.TenantKey;
import io.github.khaledshawki.eoc.tenantaccess.domain.model.TenantMembership;
import io.github.khaledshawki.eoc.tenantaccess.domain.model.TenantName;
import io.github.khaledshawki.eoc.tenantaccess.domain.model.TenantRoleKey;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class ConnectorImportAuthorizationIT {

  private static final String ISSUER = "http://localhost:8180/realms/eoc";
  private static final String SUBJECT = "connector-import-operator";
  private static final ConnectorActor ACTOR = new ConnectorActor(ISSUER, SUBJECT);

  @Autowired private ExecuteImportRunUseCase executeImportRunUseCase;
  @Autowired private ImportRunLifecycleUseCase importRunLifecycle;
  @Autowired private ConnectorRepository connectorRepository;
  @Autowired private PlatformUserRepository platformUserRepository;
  @Autowired private TenantRepository tenantRepository;
  @Autowired private TenantMembershipRepository tenantMembershipRepository;
  @Autowired private JdbcTemplate jdbcTemplate;

  @BeforeEach
  void setUp() {
    jdbcTemplate.execute(
        """
        TRUNCATE TABLE
          operations_business_partner_import_receipts,
          operations_business_partner_source_mappings,
          operations_business_partner_roles,
          operations_business_partners,
          connector_import_page_acceptances,
          connector_import_checkpoints,
          connector_import_runs,
          connectors,
          tenant_memberships,
          platform_users,
          tenants
        CASCADE
        """);
  }

  @ParameterizedTest
  @ValueSource(strings = {"tenant-admin", "operations-manager"})
  void authorizedTenantRolesShouldExecuteImports(String role) {
    Tenant tenant = createTenant("alpha");
    createMembership(tenant, createUser(SUBJECT), role);
    Connector connector = connectorRepository.save(activeConnector(tenant));
    ImportRunResult requested = requestRun(tenant, connector);

    ImportRunResult completed =
        executeImportRunUseCase.execute(
            new ExecuteImportRunCommand(
                ACTOR, tenant.id().value(), requested.importRunId().value(), 2));

    assertEquals(ImportStatus.COMPLETED, completed.status());
    assertEquals(new ImportStatistics(3, 3, 0, 0), completed.statistics());
  }

  @ParameterizedTest
  @ValueSource(strings = {"auditor", "billing-manager"})
  void nonExecutionRolesShouldBeDeniedBeforeTheRunStarts(String role) {
    Tenant tenant = createTenant("alpha");
    createMembership(tenant, createUser(SUBJECT), role);
    Connector connector = connectorRepository.save(activeConnector(tenant));
    ImportRunResult requested = requestRun(tenant, connector);

    assertThrows(
        ConnectorAccessDeniedException.class,
        () ->
            executeImportRunUseCase.execute(
                new ExecuteImportRunCommand(
                    ACTOR, tenant.id().value(), requested.importRunId().value(), 2)));

    assertRunWasNotStarted(tenant, requested);
  }

  @Test
  void membershipInAnotherTenantShouldNotAuthorizeImportExecution() {
    PlatformUser user = createUser(SUBJECT);
    Tenant allowedTenant = createTenant("alpha");
    Tenant targetTenant = createTenant("beta");
    createMembership(allowedTenant, user, "tenant-admin");
    Connector connector = connectorRepository.save(activeConnector(targetTenant));
    ImportRunResult requested = requestRun(targetTenant, connector);

    assertThrows(
        ConnectorAccessDeniedException.class,
        () ->
            executeImportRunUseCase.execute(
                new ExecuteImportRunCommand(
                    ACTOR, targetTenant.id().value(), requested.importRunId().value(), 2)));

    assertRunWasNotStarted(targetTenant, requested);
  }

  private void assertRunWasNotStarted(Tenant tenant, ImportRunResult requested) {
    ImportRunResult unchanged =
        importRunLifecycle.get(
            new ImportRunReference(tenant.id().value(), requested.importRunId().value()));

    assertEquals(ImportStatus.REQUESTED, unchanged.status());
    assertEquals(0, unchanged.attemptCount());
    assertEquals(
        0L,
        jdbcTemplate.queryForObject(
            "SELECT count(*) FROM operations_business_partner_import_receipts", Long.class));
  }

  private ImportRunResult requestRun(Tenant tenant, Connector connector) {
    return importRunLifecycle.request(
        new RequestImportRunCommand(
            tenant.id().value(),
            connector.id().value(),
            ImportType.CUSTOMERS,
            ImportMode.INCREMENTAL));
  }

  private PlatformUser createUser(String subject) {
    return platformUserRepository.save(PlatformUser.create(ExternalIdentity.of(ISSUER, subject)));
  }

  private Tenant createTenant(String key) {
    return tenantRepository.save(Tenant.create(TenantKey.of(key), TenantName.of("Tenant " + key)));
  }

  private TenantMembership createMembership(Tenant tenant, PlatformUser user, String role) {
    TenantMembership membership = TenantMembership.create(tenant.id(), user.id());
    membership.replaceRoles(Set.of(TenantRoleKey.of(role)));
    return tenantMembershipRepository.save(membership);
  }

  private static Connector activeConnector(Tenant tenant) {
    Connector connector =
        Connector.create(
            ConnectorTenantId.of(tenant.id().value()),
            ConnectorName.of("Mock ERP"),
            ConnectorType.of("mock-erp"),
            ConnectorEndpoint.of("https://mock-erp.example/api"),
            CredentialReference.of(UUID.randomUUID()),
            SyncPolicy.manual());
    connector.activate();
    return connector;
  }
}
