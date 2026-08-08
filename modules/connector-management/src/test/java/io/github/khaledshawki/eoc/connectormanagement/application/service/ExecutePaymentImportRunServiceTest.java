package io.github.khaledshawki.eoc.connectormanagement.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.khaledshawki.eoc.connectormanagement.application.model.authorization.ConnectorActor;
import io.github.khaledshawki.eoc.connectormanagement.application.model.datasource.BusinessDataSourceConfiguration;
import io.github.khaledshawki.eoc.connectormanagement.application.model.datasource.ConnectionTestResult;
import io.github.khaledshawki.eoc.connectormanagement.application.model.datasource.IncrementalCursor;
import io.github.khaledshawki.eoc.connectormanagement.application.model.datasource.SourceCustomerRecord;
import io.github.khaledshawki.eoc.connectormanagement.application.model.datasource.SourceEntity;
import io.github.khaledshawki.eoc.connectormanagement.application.model.datasource.SourceFetchRequest;
import io.github.khaledshawki.eoc.connectormanagement.application.model.datasource.SourceIdentity;
import io.github.khaledshawki.eoc.connectormanagement.application.model.datasource.SourceInvoiceRecord;
import io.github.khaledshawki.eoc.connectormanagement.application.model.datasource.SourceModificationVersion;
import io.github.khaledshawki.eoc.connectormanagement.application.model.datasource.SourcePage;
import io.github.khaledshawki.eoc.connectormanagement.application.model.datasource.SourcePageToken;
import io.github.khaledshawki.eoc.connectormanagement.application.model.datasource.SourcePaymentRecord;
import io.github.khaledshawki.eoc.connectormanagement.application.model.datasource.SourceRecordMetadata;
import io.github.khaledshawki.eoc.connectormanagement.application.model.datasource.SourceSchemaVerificationResult;
import io.github.khaledshawki.eoc.connectormanagement.application.model.event.ConnectorIntegrationEvent;
import io.github.khaledshawki.eoc.connectormanagement.application.model.importing.DownstreamImportException;
import io.github.khaledshawki.eoc.connectormanagement.application.model.importing.ImportRetryPolicy;
import io.github.khaledshawki.eoc.connectormanagement.application.model.importing.PaymentImportOutcome;
import io.github.khaledshawki.eoc.connectormanagement.application.model.importing.PaymentImportPage;
import io.github.khaledshawki.eoc.connectormanagement.application.port.in.ExecuteImportRunCommand;
import io.github.khaledshawki.eoc.connectormanagement.application.port.in.ImportRunLifecycleUseCase;
import io.github.khaledshawki.eoc.connectormanagement.application.port.in.ImportRunResult;
import io.github.khaledshawki.eoc.connectormanagement.application.port.in.RequestImportRunCommand;
import io.github.khaledshawki.eoc.connectormanagement.application.port.out.BusinessDataSource;
import io.github.khaledshawki.eoc.connectormanagement.application.port.out.ConnectorRepository;
import io.github.khaledshawki.eoc.connectormanagement.application.port.out.ImportRunRepository;
import io.github.khaledshawki.eoc.connectormanagement.application.port.out.PaymentImportPort;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.Connector;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ConnectorEndpoint;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ConnectorId;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ConnectorName;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ConnectorTenantId;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ConnectorType;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.CredentialReference;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ImportCheckpoint;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ImportCursor;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ImportFailure;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ImportFailureCategory;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ImportMode;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ImportPageAcceptanceId;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ImportRun;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ImportRunId;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ImportStatus;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ImportType;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.SyncPolicy;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Currency;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ExecutePaymentImportRunServiceTest {

  private static final Instant NOW = Instant.parse("2026-08-08T08:00:00Z");
  private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
  private static final ConnectorTenantId TENANT_ID =
      ConnectorTenantId.of(UUID.fromString("00000000-0000-0000-0000-000000000001"));
  private static final ConnectorActor ACTOR =
      new ConnectorActor("https://identity.example.com/realms/eoc", "payment-import-operator");

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
  void shouldRetrieveAcceptAndCompletePaymentPages() {
    ImportRunResult requested = requestPaymentRun();
    RecordingPaymentDataSource dataSource = new RecordingPaymentDataSource();
    RecordingPaymentImportPort paymentImportPort = new RecordingPaymentImportPort();
    ExecuteImportRunService service = service(dataSource, paymentImportPort, 3);

    ImportRunResult result =
        service.execute(
            new ExecuteImportRunCommand(
                ACTOR, TENANT_ID.value(), requested.importRunId().value(), 100));

    assertEquals(ImportStatus.COMPLETED, result.status());
    assertEquals(Optional.of(new ImportCursor("cursor-2")), result.committedCursor());
    assertEquals(2, result.statistics().fetched());
    assertEquals(1, result.statistics().accepted());
    assertEquals(0, result.statistics().rejected());
    assertEquals(1, result.statistics().duplicates());
    assertEquals(2, dataSource.requests.size());
    assertTrue(dataSource.requests.getFirst().pageToken().isEmpty());
    assertEquals(
        Optional.of(new SourcePageToken("page-2")), dataSource.requests.get(1).pageToken());
    assertEquals(2, paymentImportPort.pages.size());
    assertEquals(paymentImportPort.pages.getFirst().sourceSystemId(), connector.id().value());
    assertEquals(requested.importRunId().value(), paymentImportPort.pages.getFirst().importRunId());
    assertTrue(
        !paymentImportPort
            .pages
            .getFirst()
            .pageAcceptanceId()
            .equals(paymentImportPort.pages.get(1).pageAcceptanceId()));
  }

  @Test
  void shouldFailPermanentlyForAnInvalidDownstreamPaymentPage() {
    ImportRunResult requested = requestPaymentRun();
    ImportFailure failure =
        new ImportFailure(
            ImportFailureCategory.SOURCE_CONTRACT_VIOLATION, "invalid-payment-record");
    ExecuteImportRunService service =
        service(
            new SinglePaymentPageDataSource(),
            page -> {
              throw new DownstreamImportException(failure, new IllegalArgumentException("bad"));
            },
            3);

    ImportRunResult result =
        service.execute(
            new ExecuteImportRunCommand(
                ACTOR, TENANT_ID.value(), requested.importRunId().value(), 100));

    assertEquals(ImportStatus.FAILED, result.status());
    assertEquals(failure, result.failure().orElseThrow());
    assertTrue(result.committedCursor().isEmpty());
    assertEquals(0, result.statistics().fetched());
  }

  @Test
  void shouldScheduleRetryForARetryablePaymentPersistenceFailure() {
    ImportRunResult requested = requestPaymentRun();
    ImportFailure failure =
        new ImportFailure(
            ImportFailureCategory.DOWNSTREAM_UNAVAILABLE, "operations-temporarily-unavailable");
    ExecuteImportRunService service =
        service(
            new SinglePaymentPageDataSource(),
            page -> {
              throw new DownstreamImportException(failure, new IllegalStateException("retry"));
            },
            3);

    ImportRunResult result =
        service.execute(
            new ExecuteImportRunCommand(
                ACTOR, TENANT_ID.value(), requested.importRunId().value(), 100));

    assertEquals(ImportStatus.RETRY_SCHEDULED, result.status());
    assertEquals(failure, result.failure().orElseThrow());
    assertEquals(Optional.of(NOW.plus(Duration.ofMinutes(1))), result.nextRetryAt());
    assertTrue(result.committedCursor().isEmpty());
  }

  @Test
  void shouldRejectADifferentDownstreamAcceptanceIdWithoutRecordingProgress() {
    ImportRunResult requested = requestPaymentRun();
    ExecuteImportRunService service =
        service(
            new SinglePaymentPageDataSource(),
            page -> new PaymentImportOutcome(UUID.randomUUID(), page.records().size(), 1, 0, 0),
            3);

    assertThrows(
        IllegalStateException.class,
        () ->
            service.execute(
                new ExecuteImportRunCommand(
                    ACTOR, TENANT_ID.value(), requested.importRunId().value(), 100)));

    ImportRunResult stored =
        importRunLifecycle.get(
            new io.github.khaledshawki.eoc.connectormanagement.application.port.in
                .ImportRunReference(TENANT_ID.value(), requested.importRunId().value()));
    assertEquals(ImportStatus.RUNNING, stored.status());
    assertTrue(stored.committedCursor().isEmpty());
    assertEquals(0, stored.statistics().fetched());
  }

  private ExecuteImportRunService service(
      BusinessDataSource dataSource, PaymentImportPort paymentImportPort, int maxAttempts) {
    return new ExecuteImportRunService(
        connectorRepository,
        (actor, tenantId, permission) -> true,
        importRunLifecycle,
        connectorType -> Optional.of(dataSource),
        page -> {
          throw new AssertionError("Customer import must not run for a Payment import");
        },
        page -> {
          throw new AssertionError("Invoice import must not run for a Payment import");
        },
        paymentImportPort,
        new ImportRetryPolicy(maxAttempts, Duration.ofMinutes(1)),
        CLOCK);
  }

  private ImportRunResult requestPaymentRun() {
    return importRunLifecycle.request(
        new RequestImportRunCommand(
            TENANT_ID.value(),
            connector.id().value(),
            ImportType.PAYMENTS,
            ImportMode.INCREMENTAL));
  }

  private static SourcePaymentRecord payment(String sourceId, String amount, boolean reversed) {
    return new SourcePaymentRecord(
        new SourceRecordMetadata(
            SourceIdentity.sourceRecordId(SourceEntity.PAYMENT, sourceId),
            new SourceModificationVersion(sourceId + "-v1"),
            Optional.of(NOW)),
        SourceIdentity.sourceRecordId(SourceEntity.CUSTOMER, "customer-1"),
        LocalDate.of(2026, 8, 1),
        Currency.getInstance("EUR"),
        new BigDecimal(amount),
        reversed);
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

  private static class SinglePaymentPageDataSource implements BusinessDataSource {

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
      throw new AssertionError("Customer retrieval must not run for a Payment import");
    }

    @Override
    public SourcePage<SourceInvoiceRecord> retrieveInvoices(
        BusinessDataSourceConfiguration configuration, SourceFetchRequest fetchRequest) {
      throw new AssertionError("Invoice retrieval must not run for a Payment import");
    }

    @Override
    public SourcePage<SourcePaymentRecord> retrievePayments(
        BusinessDataSourceConfiguration configuration, SourceFetchRequest fetchRequest) {
      return new SourcePage<>(
          List.of(payment("payment-1", "100.00", false)),
          Optional.empty(),
          Optional.of(new IncrementalCursor("cursor-1")));
    }
  }

  private static final class RecordingPaymentDataSource extends SinglePaymentPageDataSource {

    private final List<SourceFetchRequest> requests = new ArrayList<>();

    @Override
    public SourcePage<SourcePaymentRecord> retrievePayments(
        BusinessDataSourceConfiguration configuration, SourceFetchRequest fetchRequest) {
      requests.add(fetchRequest);
      if (fetchRequest.pageToken().isEmpty()) {
        return new SourcePage<>(
            List.of(payment("payment-1", "100.00", false)),
            Optional.of(new SourcePageToken("page-2")),
            Optional.of(new IncrementalCursor("cursor-1")));
      }
      assertEquals(Optional.of(new SourcePageToken("page-2")), fetchRequest.pageToken());
      return new SourcePage<>(
          List.of(payment("payment-2", "50.00", true)),
          Optional.empty(),
          Optional.of(new IncrementalCursor("cursor-2")));
    }
  }

  private static final class RecordingPaymentImportPort implements PaymentImportPort {

    private final List<PaymentImportPage> pages = new ArrayList<>();

    @Override
    public PaymentImportOutcome importPage(PaymentImportPage page) {
      pages.add(page);
      if (pages.size() == 1) {
        return new PaymentImportOutcome(page.pageAcceptanceId(), 1, 1, 0, 0);
      }
      return new PaymentImportOutcome(page.pageAcceptanceId(), 1, 0, 0, 1);
    }
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
    private final Map<String, ImportCursor> checkpoints = new HashMap<>();
    private final Set<String> acceptedPages = new HashSet<>();

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
      return Optional.ofNullable(checkpoints.get(checkpointKey(tenantId, connectorId, importType)));
    }

    @Override
    public boolean hasAcceptedPage(ImportRunId importRunId, ImportPageAcceptanceId acceptanceId) {
      return acceptedPages.contains(acceptedPageKey(importRunId, acceptanceId));
    }

    @Override
    public ImportRun saveAcceptedProgress(
        ImportRun importRun,
        Optional<ImportCheckpoint> checkpoint,
        ImportPageAcceptanceId acceptanceId) {
      acceptedPages.add(acceptedPageKey(importRun.id(), acceptanceId));
      checkpoint.ifPresent(
          value ->
              checkpoints.put(
                  checkpointKey(value.tenantId(), value.connectorId(), value.importType()),
                  value.cursor()));
      return save(importRun);
    }

    private static String checkpointKey(
        ConnectorTenantId tenantId, ConnectorId connectorId, ImportType importType) {
      return tenantId.value() + ":" + connectorId.value() + ":" + importType;
    }

    private static String acceptedPageKey(
        ImportRunId importRunId, ImportPageAcceptanceId acceptanceId) {
      return importRunId.value() + ":" + acceptanceId.value();
    }
  }
}
