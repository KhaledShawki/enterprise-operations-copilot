package io.github.khaledshawki.eoc.platform.integration.connectormanagement;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.khaledshawki.eoc.connectormanagement.application.port.in.ExecuteImportRunCommand;
import io.github.khaledshawki.eoc.connectormanagement.application.port.in.ExecuteImportRunUseCase;
import io.github.khaledshawki.eoc.connectormanagement.application.port.in.ImportRunLifecycleUseCase;
import io.github.khaledshawki.eoc.connectormanagement.application.port.in.ImportRunResult;
import io.github.khaledshawki.eoc.connectormanagement.application.port.in.RequestImportRunCommand;
import io.github.khaledshawki.eoc.connectormanagement.application.port.out.ConnectorRepository;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.Connector;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ConnectorEndpoint;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ConnectorName;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ConnectorTenantId;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ConnectorType;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.CredentialReference;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ImportCursor;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ImportMode;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ImportStatistics;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ImportStatus;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ImportType;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.SyncPolicy;
import io.github.khaledshawki.eoc.platform.TestcontainersConfiguration;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class ConnectorImportOrchestrationIT {

  private static final UUID TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

  @Autowired private ConnectorRepository connectorRepository;
  @Autowired private ImportRunLifecycleUseCase importRunLifecycle;
  @Autowired private ExecuteImportRunUseCase executeImportRunUseCase;
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
          connectors
        CASCADE
        """);
  }

  @Test
  void shouldImportAllMockErpCustomersAcrossPagesAndCompleteTheRun() {
    Connector connector = activeMockErpConnector();
    connector = connectorRepository.save(connector);
    ImportRunResult requested =
        importRunLifecycle.request(
            new RequestImportRunCommand(
                TENANT_ID, connector.id().value(), ImportType.CUSTOMERS, ImportMode.INCREMENTAL));

    ImportRunResult completed =
        executeImportRunUseCase.execute(
            new ExecuteImportRunCommand(TENANT_ID, requested.importRunId().value(), 2));

    assertEquals(ImportStatus.COMPLETED, completed.status());
    assertEquals(new ImportStatistics(3, 3, 0, 0), completed.statistics());
    assertEquals(Optional.of(new ImportCursor("mock-erp|customer|3")), completed.committedCursor());
    assertEquals(
        3L,
        jdbcTemplate.queryForObject(
            "SELECT count(*) FROM operations_business_partners", Long.class));
    assertEquals(
        3L,
        jdbcTemplate.queryForObject(
            "SELECT count(*) FROM operations_business_partner_source_mappings", Long.class));
    assertEquals(
        2L,
        jdbcTemplate.queryForObject(
            "SELECT count(*) FROM operations_business_partner_import_receipts", Long.class));
    assertEquals(
        2L,
        jdbcTemplate.queryForObject(
            "SELECT count(*) FROM connector_import_page_acceptances", Long.class));

    ImportRunResult replay =
        executeImportRunUseCase.execute(
            new ExecuteImportRunCommand(TENANT_ID, requested.importRunId().value(), 2));
    assertEquals(completed, replay);
    assertEquals(
        2L,
        jdbcTemplate.queryForObject(
            "SELECT count(*) FROM operations_business_partner_import_receipts", Long.class));
  }

  private static Connector activeMockErpConnector() {
    Connector connector =
        Connector.create(
            ConnectorTenantId.of(TENANT_ID),
            ConnectorName.of("Mock ERP"),
            ConnectorType.of("mock-erp"),
            ConnectorEndpoint.of("https://mock-erp.example/api"),
            CredentialReference.of(UUID.randomUUID()),
            SyncPolicy.manual());
    connector.activate();
    return connector;
  }
}
