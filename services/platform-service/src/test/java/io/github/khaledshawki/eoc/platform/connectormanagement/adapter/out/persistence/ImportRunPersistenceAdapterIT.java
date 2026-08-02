package io.github.khaledshawki.eoc.platform.connectormanagement.adapter.out.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.khaledshawki.eoc.connectormanagement.application.exception.ActiveImportRunAlreadyExistsException;
import io.github.khaledshawki.eoc.connectormanagement.application.exception.ConcurrentImportRunModificationException;
import io.github.khaledshawki.eoc.connectormanagement.application.exception.ImportPageAlreadyAcceptedException;
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
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ImportMode;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ImportPageAcceptanceId;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ImportRun;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ImportStatistics;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ImportStatus;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ImportType;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.SyncPolicy;
import io.github.khaledshawki.eoc.platform.TestcontainersConfiguration;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
@Import({
  TestcontainersConfiguration.class,
  ImportRunPersistenceAdapterIT.FixedClockConfiguration.class
})
class ImportRunPersistenceAdapterIT {

  private static final Instant NOW = Instant.parse("2026-08-02T10:00:00Z");
  private static final ConnectorTenantId TENANT_ID =
      ConnectorTenantId.of(UUID.fromString("00000000-0000-0000-0000-000000000010"));
  private static final ConnectorTenantId OTHER_TENANT_ID =
      ConnectorTenantId.of(UUID.fromString("00000000-0000-0000-0000-000000000011"));

  @Autowired private ConnectorRepository connectorRepository;
  @Autowired private ImportRunRepository importRunRepository;
  @Autowired private SpringDataConnectorRepository springDataConnectorRepository;
  @Autowired private SpringDataImportRunRepository springDataImportRunRepository;
  @Autowired private SpringDataImportCheckpointRepository checkpointRepository;
  @Autowired private SpringDataImportPageAcceptanceRepository acceptanceRepository;
  @Autowired private JdbcTemplate jdbcTemplate;

  private Connector connector;

  @BeforeEach
  void setUp() {
    acceptanceRepository.deleteAllInBatch();
    checkpointRepository.deleteAllInBatch();
    springDataImportRunRepository.deleteAllInBatch();
    springDataConnectorRepository.deleteAllInBatch();
    connector = connectorRepository.save(activeConnector(TENANT_ID, "Primary ERP"));
  }

  @Test
  void shouldPersistAndLoadARequestedRunWithinItsTenant() {
    ImportRun requested = requestedRun(connector.id(), ImportType.CUSTOMERS);

    ImportRun saved = importRunRepository.save(requested);

    assertEquals(ImportStatus.REQUESTED, saved.status());
    assertEquals(0, saved.version());
    assertTrue(importRunRepository.existsActive(TENANT_ID, connector.id(), ImportType.CUSTOMERS));
    assertFalse(importRunRepository.existsActive(TENANT_ID, connector.id(), ImportType.INVOICES));
    assertTrue(importRunRepository.findById(OTHER_TENANT_ID, saved.id()).isEmpty());

    ImportRun loaded = importRunRepository.findById(TENANT_ID, saved.id()).orElseThrow();
    assertEquals(saved.id(), loaded.id());
    assertEquals(saved.tenantId(), loaded.tenantId());
    assertEquals(saved.connectorId(), loaded.connectorId());
    assertEquals(saved.importType(), loaded.importType());
    assertEquals(saved.mode(), loaded.mode());
    assertEquals(saved.requestedAt(), loaded.requestedAt());

    ImportRunJpaEntity stored =
        springDataImportRunRepository.findById(saved.id().value()).orElseThrow();
    assertEquals(NOW, stored.getCreatedAt());
    assertEquals(NOW, stored.getUpdatedAt());
  }

  @Test
  void shouldEnforceOneActiveRunPerConnectorSourcePartition() {
    importRunRepository.save(requestedRun(connector.id(), ImportType.CUSTOMERS));

    ActiveImportRunAlreadyExistsException exception =
        assertThrows(
            ActiveImportRunAlreadyExistsException.class,
            () -> importRunRepository.save(requestedRun(connector.id(), ImportType.CUSTOMERS)));

    assertInstanceOf(DataIntegrityViolationException.class, exception.getCause());
    importRunRepository.save(requestedRun(connector.id(), ImportType.INVOICES));

    Connector second = connectorRepository.save(activeConnector(TENANT_ID, "Secondary ERP"));
    importRunRepository.save(requestedRun(second.id(), ImportType.CUSTOMERS));

    assertEquals(3, springDataImportRunRepository.count());
  }

  @Test
  void shouldCommitRunCheckpointAndAcceptanceReceiptAtomically() {
    ImportRun running = runningRun();
    ImportPageAcceptanceId acceptanceId =
        ImportPageAcceptanceId.of(UUID.fromString("00000000-0000-0000-0000-000000000030"));
    ImportCursor cursor = new ImportCursor("customer-3");
    running.recordAcceptedPage(
        Optional.empty(), Optional.of(cursor), new ImportStatistics(3, 2, 0, 1));

    ImportRun saved =
        importRunRepository.saveAcceptedProgress(
            running,
            Optional.of(
                new ImportCheckpoint(TENANT_ID, connector.id(), ImportType.CUSTOMERS, cursor)),
            acceptanceId);

    assertEquals(2, saved.version());
    assertEquals(new ImportStatistics(3, 2, 0, 1), saved.statistics());
    assertEquals(
        Optional.of(cursor),
        importRunRepository.findCheckpoint(TENANT_ID, connector.id(), ImportType.CUSTOMERS));
    assertTrue(importRunRepository.hasAcceptedPage(saved.id(), acceptanceId));
    assertEquals(1, acceptanceRepository.count());
    assertEquals(1, checkpointRepository.count());
  }

  @Test
  void shouldRollBackAReplayedAcceptanceWithoutDoubleCountingProgress() {
    ImportRun running = runningRun();
    ImportPageAcceptanceId acceptanceId = ImportPageAcceptanceId.of(UUID.randomUUID());
    ImportCursor firstCursor = new ImportCursor("customer-1");
    running.recordAcceptedPage(
        Optional.empty(), Optional.of(firstCursor), new ImportStatistics(1, 1, 0, 0));
    ImportRun firstSaved =
        importRunRepository.saveAcceptedProgress(
            running,
            Optional.of(
                new ImportCheckpoint(TENANT_ID, connector.id(), ImportType.CUSTOMERS, firstCursor)),
            acceptanceId);

    ImportRun replay = importRunRepository.findById(TENANT_ID, firstSaved.id()).orElseThrow();
    ImportCursor secondCursor = new ImportCursor("customer-2");
    replay.recordAcceptedPage(
        Optional.of(firstCursor), Optional.of(secondCursor), new ImportStatistics(1, 1, 0, 0));

    assertThrows(
        ImportPageAlreadyAcceptedException.class,
        () ->
            importRunRepository.saveAcceptedProgress(
                replay,
                Optional.of(
                    new ImportCheckpoint(
                        TENANT_ID, connector.id(), ImportType.CUSTOMERS, secondCursor)),
                acceptanceId));

    ImportRun unchanged = importRunRepository.findById(TENANT_ID, firstSaved.id()).orElseThrow();
    assertEquals(new ImportStatistics(1, 1, 0, 0), unchanged.statistics());
    assertEquals(Optional.of(firstCursor), unchanged.committedCursor());
    assertEquals(
        Optional.of(firstCursor),
        importRunRepository.findCheckpoint(TENANT_ID, connector.id(), ImportType.CUSTOMERS));
    assertEquals(1, acceptanceRepository.count());
  }

  @Test
  void shouldRejectAStaleAggregateVersionBeforeItCanOverwriteCurrentState() {
    ImportRun requested =
        importRunRepository.save(requestedRun(connector.id(), ImportType.CUSTOMERS));
    ImportRun first = importRunRepository.findById(TENANT_ID, requested.id()).orElseThrow();
    ImportRun stale = importRunRepository.findById(TENANT_ID, requested.id()).orElseThrow();
    first.start(NOW);
    stale.start(NOW);

    ImportRun saved = importRunRepository.save(first);

    assertEquals(1, saved.version());
    assertThrows(
        ConcurrentImportRunModificationException.class, () -> importRunRepository.save(stale));
    assertEquals(
        ImportStatus.RUNNING,
        importRunRepository.findById(TENANT_ID, requested.id()).orElseThrow().status());
  }

  @Test
  void shouldEnforceTenantConnectorAndStatisticsInvariantsInPostgres() {
    Timestamp now = Timestamp.from(NOW);
    DataIntegrityViolationException invalidStatistics =
        assertThrows(
            DataIntegrityViolationException.class,
            () ->
                jdbcTemplate.update(
                    """
                    INSERT INTO connector_import_runs (
                      id, tenant_id, connector_id, import_type, import_mode, status,
                      fetched_count, accepted_count, rejected_count, duplicate_count,
                      requested_at, created_at, updated_at
                    ) VALUES (?, ?, ?, 'CUSTOMERS', 'FULL', 'REQUESTED', 0, 1, 0, 0, ?, ?, ?)
                    """,
                    UUID.randomUUID(),
                    TENANT_ID.value(),
                    connector.id().value(),
                    now,
                    now,
                    now));
    String constraintMessage = invalidStatistics.getMostSpecificCause().getMessage();
    assertTrue(
        constraintMessage.contains("ck_connector_import_runs_statistics"), constraintMessage);

    assertThrows(
        DataIntegrityViolationException.class,
        () ->
            jdbcTemplate.update(
                """
                INSERT INTO connector_import_runs (
                  id, tenant_id, connector_id, import_type, import_mode, status,
                  requested_at, created_at, updated_at
                ) VALUES (?, ?, ?, 'CUSTOMERS', 'FULL', 'REQUESTED', ?, ?, ?)
                """,
                UUID.randomUUID(),
                OTHER_TENANT_ID.value(),
                connector.id().value(),
                now,
                now,
                now));
  }

  @Test
  void shouldApplyTheImportRunMigration() {
    Integer migrationCount =
        jdbcTemplate.queryForObject(
            "SELECT count(*) FROM flyway_schema_history WHERE version = '5' AND success",
            Integer.class);
    assertEquals(1, migrationCount.intValue());
  }

  private ImportRun runningRun() {
    ImportRun run = importRunRepository.save(requestedRun(connector.id(), ImportType.CUSTOMERS));
    run.start(NOW);
    return importRunRepository.save(run);
  }

  private static ImportRun requestedRun(ConnectorId connectorId, ImportType importType) {
    return ImportRun.request(
        TENANT_ID, connectorId, importType, ImportMode.INCREMENTAL, Optional.empty(), NOW);
  }

  private static Connector activeConnector(ConnectorTenantId tenantId, String name) {
    Connector connector =
        Connector.create(
            tenantId,
            ConnectorName.of(name),
            ConnectorType.of("mock-erp"),
            ConnectorEndpoint.of("https://erp.example.com/api"),
            CredentialReference.of(UUID.randomUUID()),
            SyncPolicy.manual());
    connector.activate();
    return connector;
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class FixedClockConfiguration {

    @Bean
    @Primary
    Clock importRunPersistenceClock() {
      return Clock.fixed(NOW, ZoneOffset.UTC);
    }
  }
}
