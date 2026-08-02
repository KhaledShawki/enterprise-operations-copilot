package io.github.khaledshawki.eoc.connectormanagement.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.khaledshawki.eoc.connectormanagement.application.exception.ActiveImportRunAlreadyExistsException;
import io.github.khaledshawki.eoc.connectormanagement.application.exception.ConnectorCannotStartImportException;
import io.github.khaledshawki.eoc.connectormanagement.application.exception.ConnectorNotFoundException;
import io.github.khaledshawki.eoc.connectormanagement.application.exception.ImportRunNotFoundException;
import io.github.khaledshawki.eoc.connectormanagement.application.port.in.ImportRunReference;
import io.github.khaledshawki.eoc.connectormanagement.application.port.in.ImportRunResult;
import io.github.khaledshawki.eoc.connectormanagement.application.port.in.RecordAcceptedImportPageCommand;
import io.github.khaledshawki.eoc.connectormanagement.application.port.in.RequestImportRunCommand;
import io.github.khaledshawki.eoc.connectormanagement.application.port.in.ScheduleImportRetryCommand;
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
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ImportFailure;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ImportFailureCategory;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ImportMode;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ImportPageAcceptanceId;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ImportRun;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ImportRunId;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ImportStatistics;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ImportStatus;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ImportType;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.SyncPolicy;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ImportRunLifecycleServiceTest {

  private static final Instant NOW = Instant.parse("2026-08-02T09:00:00Z");
  private static final ConnectorTenantId TENANT_ID =
      ConnectorTenantId.of(UUID.fromString("00000000-0000-0000-0000-000000000010"));
  private static final ConnectorTenantId OTHER_TENANT_ID =
      ConnectorTenantId.of(UUID.fromString("00000000-0000-0000-0000-000000000011"));

  private InMemoryConnectorRepository connectorRepository;
  private InMemoryImportRunRepository importRunRepository;
  private ImportRunLifecycleService service;
  private Connector connector;

  @BeforeEach
  void setUp() {
    connectorRepository = new InMemoryConnectorRepository();
    importRunRepository = new InMemoryImportRunRepository();
    service =
        new ImportRunLifecycleService(
            connectorRepository, importRunRepository, Clock.fixed(NOW, ZoneOffset.UTC));
    connector = activeConnector(TENANT_ID);
    connectorRepository.save(connector);
  }

  @Test
  void shouldRequestFullImportsWithoutReusingAnIncrementalCheckpoint() {
    importRunRepository.checkpoint = Optional.of(new ImportCursor("customer-100"));

    ImportRunResult result =
        service.request(
            new RequestImportRunCommand(
                TENANT_ID.value(), connector.id().value(), ImportType.CUSTOMERS, ImportMode.FULL));

    assertEquals(ImportStatus.REQUESTED, result.status());
    assertTrue(result.committedCursor().isEmpty());
    assertEquals(NOW, result.requestedAt());
  }

  @Test
  void shouldResumeIncrementalImportsFromTheLastDurableCheckpoint() {
    importRunRepository.checkpoint = Optional.of(new ImportCursor("customer-100"));

    ImportRunResult result =
        service.request(
            new RequestImportRunCommand(
                TENANT_ID.value(),
                connector.id().value(),
                ImportType.CUSTOMERS,
                ImportMode.INCREMENTAL));

    assertEquals(Optional.of(new ImportCursor("customer-100")), result.committedCursor());
  }

  @Test
  void shouldRequireAnActiveTenantScopedConnector() {
    Connector draft = draftConnector(TENANT_ID);
    connectorRepository.save(draft);

    assertThrows(
        ConnectorCannotStartImportException.class,
        () ->
            service.request(
                new RequestImportRunCommand(
                    TENANT_ID.value(), draft.id().value(), ImportType.INVOICES, ImportMode.FULL)));
    assertThrows(
        ConnectorNotFoundException.class,
        () ->
            service.request(
                new RequestImportRunCommand(
                    OTHER_TENANT_ID.value(),
                    connector.id().value(),
                    ImportType.INVOICES,
                    ImportMode.FULL)));
  }

  @Test
  void shouldRecheckConnectorStatusBeforeStartingQueuedOrRetryWork() {
    ImportRunResult requested = requestCustomers();
    connector.suspend();

    assertThrows(
        ConnectorCannotStartImportException.class, () -> service.start(reference(requested)));
    assertEquals(ImportStatus.REQUESTED, service.get(reference(requested)).status());
  }

  @Test
  void shouldRejectASecondActiveRunForTheSameSourcePartition() {
    RequestImportRunCommand command =
        new RequestImportRunCommand(
            TENANT_ID.value(),
            connector.id().value(),
            ImportType.CUSTOMERS,
            ImportMode.INCREMENTAL);
    service.request(command);

    assertThrows(ActiveImportRunAlreadyExistsException.class, () -> service.request(command));

    ImportRunResult invoiceRun =
        service.request(
            new RequestImportRunCommand(
                TENANT_ID.value(),
                connector.id().value(),
                ImportType.INVOICES,
                ImportMode.INCREMENTAL));
    assertEquals(ImportType.INVOICES, invoiceRun.importType());
  }

  @Test
  void shouldRecordAcceptedProgressAtomicallyAndIdempotently() {
    ImportRunResult requested = requestCustomers();
    ImportRunReference reference = reference(requested);
    service.start(reference);
    UUID acceptanceId = UUID.fromString("00000000-0000-0000-0000-000000000030");
    RecordAcceptedImportPageCommand command =
        new RecordAcceptedImportPageCommand(
            TENANT_ID.value(),
            requested.importRunId().value(),
            acceptanceId,
            Optional.empty(),
            Optional.of("customer-3"),
            new ImportStatistics(3, 2, 0, 1));

    ImportRunResult first = service.recordAcceptedPage(command);
    ImportRunResult replay = service.recordAcceptedPage(command);

    assertEquals(new ImportStatistics(3, 2, 0, 1), first.statistics());
    assertEquals(first.statistics(), replay.statistics());
    assertEquals(Optional.of(new ImportCursor("customer-3")), replay.committedCursor());
    assertEquals(Optional.of(new ImportCursor("customer-3")), importRunRepository.checkpoint);
    assertEquals(1, importRunRepository.acceptances.size());
  }

  @Test
  void shouldRejectProgressBasedOnAStaleCursorWithoutChangingTheCheckpoint() {
    ImportRunResult requested = requestCustomers();
    service.start(reference(requested));

    assertThrows(
        IllegalStateException.class,
        () ->
            service.recordAcceptedPage(
                new RecordAcceptedImportPageCommand(
                    TENANT_ID.value(),
                    requested.importRunId().value(),
                    UUID.randomUUID(),
                    Optional.of("stale"),
                    Optional.of("customer-3"),
                    new ImportStatistics(1, 1, 0, 0))));

    assertTrue(importRunRepository.checkpoint.isEmpty());
    assertTrue(importRunRepository.acceptances.isEmpty());
  }

  @Test
  void shouldManageRetryAndCancellationTransitionsThroughTheUseCase() {
    ImportRunResult requested = requestCustomers();
    ImportRunReference reference = reference(requested);
    service.start(reference);
    ImportFailure failure =
        new ImportFailure(ImportFailureCategory.SOURCE_UNAVAILABLE, "source-unavailable");

    ImportRunResult retry =
        service.scheduleRetry(
            new ScheduleImportRetryCommand(
                TENANT_ID.value(), requested.importRunId().value(), failure, NOW.plusSeconds(30)));
    ImportRunResult cancelled = service.requestCancellation(reference);

    assertEquals(ImportStatus.RETRY_SCHEDULED, retry.status());
    assertEquals(ImportStatus.CANCELLED, cancelled.status());
    assertTrue(cancelled.failure().isEmpty());
  }

  @Test
  void shouldNotRevealRunsAcrossTenantBoundaries() {
    ImportRunResult requested = requestCustomers();

    assertThrows(
        ImportRunNotFoundException.class,
        () ->
            service.get(
                new ImportRunReference(OTHER_TENANT_ID.value(), requested.importRunId().value())));
  }

  private ImportRunResult requestCustomers() {
    return service.request(
        new RequestImportRunCommand(
            TENANT_ID.value(),
            connector.id().value(),
            ImportType.CUSTOMERS,
            ImportMode.INCREMENTAL));
  }

  private static ImportRunReference reference(ImportRunResult result) {
    return new ImportRunReference(result.tenantId().value(), result.importRunId().value());
  }

  private static Connector activeConnector(ConnectorTenantId tenantId) {
    Connector connector = draftConnector(tenantId);
    connector.activate();
    return connector;
  }

  private static Connector draftConnector(ConnectorTenantId tenantId) {
    return Connector.create(
        tenantId,
        ConnectorName.of("Primary ERP " + UUID.randomUUID()),
        ConnectorType.of("mock-erp"),
        ConnectorEndpoint.of("https://erp.example.com/api"),
        CredentialReference.of(UUID.randomUUID()),
        SyncPolicy.manual());
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

    private final Map<ImportRunId, ImportRun> runs = new HashMap<>();
    private final Set<String> acceptances = new HashSet<>();
    private Optional<ImportCursor> checkpoint = Optional.empty();

    @Override
    public ImportRun save(ImportRun importRun) {
      runs.put(importRun.id(), importRun);
      return importRun;
    }

    @Override
    public Optional<ImportRun> findById(ConnectorTenantId tenantId, ImportRunId importRunId) {
      return Optional.ofNullable(runs.get(importRunId))
          .filter(importRun -> importRun.tenantId().equals(tenantId));
    }

    @Override
    public boolean existsActive(
        ConnectorTenantId tenantId, ConnectorId connectorId, ImportType importType) {
      return runs.values().stream()
          .anyMatch(
              run ->
                  run.tenantId().equals(tenantId)
                      && run.connectorId().equals(connectorId)
                      && run.importType() == importType
                      && !run.status().terminal());
    }

    @Override
    public Optional<ImportCursor> findCheckpoint(
        ConnectorTenantId tenantId, ConnectorId connectorId, ImportType importType) {
      return checkpoint;
    }

    @Override
    public boolean hasAcceptedPage(ImportRunId importRunId, ImportPageAcceptanceId acceptanceId) {
      return acceptances.contains(key(importRunId, acceptanceId));
    }

    @Override
    public ImportRun saveAcceptedProgress(
        ImportRun importRun,
        Optional<ImportCheckpoint> checkpoint,
        ImportPageAcceptanceId acceptanceId) {
      acceptances.add(key(importRun.id(), acceptanceId));
      checkpoint.ifPresent(value -> this.checkpoint = Optional.of(value.cursor()));
      return save(importRun);
    }

    private static String key(ImportRunId importRunId, ImportPageAcceptanceId acceptanceId) {
      return importRunId.value() + ":" + acceptanceId.value();
    }
  }
}
