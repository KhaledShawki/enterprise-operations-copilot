package io.github.khaledshawki.eoc.connectormanagement.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.khaledshawki.eoc.connectormanagement.application.model.authorization.ConnectorActor;
import io.github.khaledshawki.eoc.connectormanagement.application.model.datasource.BusinessDataSourceConfiguration;
import io.github.khaledshawki.eoc.connectormanagement.application.model.datasource.BusinessDataSourceException;
import io.github.khaledshawki.eoc.connectormanagement.application.model.datasource.BusinessDataSourceFailure;
import io.github.khaledshawki.eoc.connectormanagement.application.model.datasource.ConnectionTestResult;
import io.github.khaledshawki.eoc.connectormanagement.application.model.datasource.SourceCustomerRecord;
import io.github.khaledshawki.eoc.connectormanagement.application.model.datasource.SourceFetchRequest;
import io.github.khaledshawki.eoc.connectormanagement.application.model.datasource.SourceInvoiceRecord;
import io.github.khaledshawki.eoc.connectormanagement.application.model.datasource.SourcePage;
import io.github.khaledshawki.eoc.connectormanagement.application.model.datasource.SourcePaymentRecord;
import io.github.khaledshawki.eoc.connectormanagement.application.model.datasource.SourceSchemaVerificationResult;
import io.github.khaledshawki.eoc.connectormanagement.application.model.event.ConnectorIntegrationEvent;
import io.github.khaledshawki.eoc.connectormanagement.application.model.importing.ImportRetryPolicy;
import io.github.khaledshawki.eoc.connectormanagement.application.port.in.ExecuteImportRunCommand;
import io.github.khaledshawki.eoc.connectormanagement.application.port.in.ImportRunLifecycleUseCase;
import io.github.khaledshawki.eoc.connectormanagement.application.port.in.ImportRunResult;
import io.github.khaledshawki.eoc.connectormanagement.application.port.in.RequestImportRunCommand;
import io.github.khaledshawki.eoc.connectormanagement.application.port.out.BusinessDataSource;
import io.github.khaledshawki.eoc.connectormanagement.application.port.out.ConnectorRepository;
import io.github.khaledshawki.eoc.connectormanagement.application.port.out.ImportRunRepository;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.Connector;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ConnectorEndpoint;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ConnectorId;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ConnectorName;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ConnectorTenantId;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ConnectorType;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.CredentialReference;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ImportCheckpoint;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ImportCursor;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ImportFailureCategory;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ImportMode;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ImportPageAcceptanceId;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ImportRun;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ImportRunId;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ImportStatus;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ImportType;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.SyncPolicy;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ExecuteImportRunServiceTest {

  private static final Instant NOW = Instant.parse("2026-08-02T12:00:00Z");
  private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
  private static final ConnectorTenantId TENANT_ID =
      ConnectorTenantId.of(UUID.fromString("00000000-0000-0000-0000-000000000001"));
  private static final ConnectorActor ACTOR =
      new ConnectorActor("https://identity.example.com/realms/eoc", "import-operator");

  private InMemoryConnectorRepository connectorRepository;
  private InMemoryImportRunRepository importRunRepository;
  private ImportRunLifecycleUseCase importRunLifecycle;
  private Connector connector;

  @BeforeEach
  void setUp() {
    connectorRepository = new InMemoryConnectorRepository();
    importRunRepository = new InMemoryImportRunRepository();
    importRunLifecycle =
        new ImportRunLifecycleService(
            connectorRepository,
            importRunRepository,
            () -> UUID.fromString("00000000-0000-0000-0000-000000000050"),
            CLOCK);
    connector = activeConnector();
    connectorRepository.save(connector);
  }

  @Test
  void shouldScheduleARetryForARetryableSourceFailureWithoutAdvancingTheCursor() {
    ImportRunResult requested = requestRun();
    BusinessDataSourceFailure sourceFailure =
        new BusinessDataSourceFailure(
            BusinessDataSourceFailure.Category.SOURCE_UNAVAILABLE,
            "source-temporarily-unavailable");
    ExecuteImportRunService service = service(failingDataSource(sourceFailure), 3);

    ImportRunResult result =
        service.execute(
            new ExecuteImportRunCommand(
                ACTOR, TENANT_ID.value(), requested.importRunId().value(), 100));

    assertEquals(ImportStatus.RETRY_SCHEDULED, result.status());
    assertEquals(1, result.attemptCount());
    assertTrue(result.committedCursor().isEmpty());
    assertEquals(
        ImportFailureCategory.SOURCE_UNAVAILABLE, result.failure().orElseThrow().category());
    assertEquals("source-temporarily-unavailable", result.failure().orElseThrow().diagnosticCode());
    assertEquals(Optional.of(NOW.plus(Duration.ofMinutes(1))), result.nextRetryAt());
  }

  @Test
  void shouldFailImmediatelyForAPermanentSourceFailure() {
    ImportRunResult requested = requestRun();
    BusinessDataSourceFailure sourceFailure =
        new BusinessDataSourceFailure(
            BusinessDataSourceFailure.Category.AUTHENTICATION_FAILED,
            "source-authentication-failed");
    ExecuteImportRunService service = service(failingDataSource(sourceFailure), 3);

    ImportRunResult result =
        service.execute(
            new ExecuteImportRunCommand(
                ACTOR, TENANT_ID.value(), requested.importRunId().value(), 100));

    assertEquals(ImportStatus.FAILED, result.status());
    assertEquals(1, result.attemptCount());
    assertEquals(
        ImportFailureCategory.AUTHENTICATION_FAILED, result.failure().orElseThrow().category());
    assertTrue(result.nextRetryAt().isEmpty());
  }

  @Test
  void shouldStopRetryingWhenTheAttemptBudgetIsExhausted() {
    ImportRunResult requested = requestRun();
    BusinessDataSourceFailure sourceFailure =
        new BusinessDataSourceFailure(
            BusinessDataSourceFailure.Category.TIMEOUT, "source-request-timeout");
    ExecuteImportRunService service = service(failingDataSource(sourceFailure), 1);

    ImportRunResult result =
        service.execute(
            new ExecuteImportRunCommand(
                ACTOR, TENANT_ID.value(), requested.importRunId().value(), 100));

    assertEquals(ImportStatus.FAILED, result.status());
    assertEquals(ImportFailureCategory.TIMEOUT, result.failure().orElseThrow().category());
    assertTrue(result.nextRetryAt().isEmpty());
  }

  private ExecuteImportRunService service(BusinessDataSource dataSource, int maxAttempts) {
    return new ExecuteImportRunService(
        connectorRepository,
        (actor, tenantId, permission) -> true,
        importRunLifecycle,
        connectorType -> Optional.of(dataSource),
        page -> {
          throw new AssertionError("Downstream import must not run after a source failure");
        },
        page -> {
          throw new AssertionError("Invoice import must not run after a customer source failure");
        },
        page -> {
          throw new AssertionError("Payment import must not run after a customer source failure");
        },
        new ImportRetryPolicy(maxAttempts, Duration.ofMinutes(1)),
        CLOCK);
  }

  private ImportRunResult requestRun() {
    return importRunLifecycle.request(
        new RequestImportRunCommand(
            TENANT_ID.value(),
            connector.id().value(),
            ImportType.CUSTOMERS,
            ImportMode.INCREMENTAL));
  }

  private static BusinessDataSource failingDataSource(BusinessDataSourceFailure failure) {
    return new BusinessDataSource() {
      @Override
      public ConnectorType supportedConnectorType() {
        return ConnectorType.of("mock-erp");
      }

      @Override
      public ConnectionTestResult testConnection(BusinessDataSourceConfiguration configuration) {
        return ConnectionTestResult.connected();
      }

      @Override
      public SourceSchemaVerificationResult verifySourceSchema(
          BusinessDataSourceConfiguration configuration) {
        return SourceSchemaVerificationResult.verified();
      }

      @Override
      public SourcePage<SourceCustomerRecord> retrieveCustomers(
          BusinessDataSourceConfiguration configuration, SourceFetchRequest fetchRequest) {
        throw new BusinessDataSourceException(failure);
      }

      @Override
      public SourcePage<SourceInvoiceRecord> retrieveInvoices(
          BusinessDataSourceConfiguration configuration, SourceFetchRequest fetchRequest) {
        throw new AssertionError("Invoice retrieval must not run for a customer import");
      }

      @Override
      public SourcePage<SourcePaymentRecord> retrievePayments(
          BusinessDataSourceConfiguration configuration, SourceFetchRequest fetchRequest) {
        throw new AssertionError("Payment retrieval must not run for a customer import");
      }
    };
  }

  private static Connector activeConnector() {
    Connector connector =
        Connector.create(
            TENANT_ID,
            ConnectorName.of("Mock ERP"),
            ConnectorType.of("mock-erp"),
            ConnectorEndpoint.of("https://mock-erp.example/api"),
            CredentialReference.of(UUID.randomUUID()),
            SyncPolicy.manual());
    connector.activate();
    return connector;
  }

  private static final class InMemoryConnectorRepository implements ConnectorRepository {

    private final Map<ConnectorId, Connector> connectors = new HashMap<>();

    @Override
    public Connector save(Connector connector) {
      connectors.put(connector.id(), connector);
      return connector;
    }

    @Override
    public Optional<Connector> findById(ConnectorTenantId tenantId, ConnectorId connectorId) {
      return Optional.ofNullable(connectors.get(connectorId))
          .filter(connector -> connector.tenantId().equals(tenantId));
    }

    @Override
    public List<Connector> findAllByTenantId(ConnectorTenantId tenantId) {
      return connectors.values().stream()
          .filter(connector -> connector.tenantId().equals(tenantId))
          .toList();
    }

    @Override
    public boolean existsByTenantIdAndName(
        ConnectorTenantId tenantId, ConnectorName connectorName) {
      return connectors.values().stream()
          .anyMatch(
              connector ->
                  connector.tenantId().equals(tenantId) && connector.name().equals(connectorName));
    }
  }

  private static final class InMemoryImportRunRepository implements ImportRunRepository {

    private final Map<ImportRunId, ImportRun> importRuns = new HashMap<>();

    @Override
    public ImportRun save(ImportRun importRun) {
      importRuns.put(importRun.id(), importRun);
      return importRun;
    }

    @Override
    public ImportRun saveWithEvent(ImportRun importRun, ConnectorIntegrationEvent event) {
      return save(importRun);
    }

    @Override
    public Optional<ImportRun> findById(ConnectorTenantId tenantId, ImportRunId importRunId) {
      return Optional.ofNullable(importRuns.get(importRunId))
          .filter(importRun -> importRun.tenantId().equals(tenantId));
    }

    @Override
    public boolean existsActive(
        ConnectorTenantId tenantId, ConnectorId connectorId, ImportType importType) {
      return importRuns.values().stream()
          .anyMatch(
              importRun ->
                  importRun.tenantId().equals(tenantId)
                      && importRun.connectorId().equals(connectorId)
                      && importRun.importType() == importType
                      && !importRun.status().terminal());
    }

    @Override
    public Optional<ImportCursor> findCheckpoint(
        ConnectorTenantId tenantId, ConnectorId connectorId, ImportType importType) {
      return Optional.empty();
    }

    @Override
    public boolean hasAcceptedPage(ImportRunId importRunId, ImportPageAcceptanceId acceptanceId) {
      return false;
    }

    @Override
    public ImportRun saveAcceptedProgress(
        ImportRun importRun,
        Optional<ImportCheckpoint> checkpoint,
        ImportPageAcceptanceId acceptanceId) {
      throw new UnsupportedOperationException("Accepted progress is not used by these tests");
    }
  }
}
