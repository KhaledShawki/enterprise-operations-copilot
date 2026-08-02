package io.github.khaledshawki.eoc.platform.connectormanagement.adapter.out.persistence;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.khaledshawki.eoc.connectormanagement.application.exception.ConcurrentImportRunModificationException;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ConnectorId;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ConnectorTenantId;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ImportCursor;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ImportFailure;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ImportFailureCategory;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ImportMode;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ImportRun;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ImportRunId;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ImportStatistics;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ImportStatus;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ImportType;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ImportRunPersistenceMapperTest {

  private static final Instant REQUESTED_AT = Instant.parse("2026-08-02T08:00:00Z");
  private static final Instant STARTED_AT = Instant.parse("2026-08-02T08:01:00Z");
  private static final Instant NOW = Instant.parse("2026-08-02T08:02:00Z");
  private static final ImportRunId IMPORT_RUN_ID =
      ImportRunId.of(UUID.fromString("00000000-0000-0000-0000-000000000001"));
  private static final ConnectorTenantId TENANT_ID =
      ConnectorTenantId.of(UUID.fromString("00000000-0000-0000-0000-000000000002"));
  private static final ConnectorId CONNECTOR_ID =
      ConnectorId.of(UUID.fromString("00000000-0000-0000-0000-000000000003"));

  private final ImportRunPersistenceMapper mapper = new ImportRunPersistenceMapper();

  @Test
  void shouldMapARequestedRunToItsPrivatePersistenceRepresentation() {
    ImportRun run = requestedRun();

    ImportRunJpaEntity entity = mapper.toEntity(run, NOW);

    assertAll(
        () -> assertEquals(IMPORT_RUN_ID.value(), entity.getId()),
        () -> assertEquals(TENANT_ID.value(), entity.getTenantId()),
        () -> assertEquals(CONNECTOR_ID.value(), entity.getConnectorId()),
        () -> assertEquals(ImportType.CUSTOMERS, entity.getImportType()),
        () -> assertEquals(ImportMode.INCREMENTAL, entity.getImportMode()),
        () -> assertEquals(ImportStatus.REQUESTED, entity.getStatus()),
        () -> assertEquals("customer-100", entity.getCommittedCursor()),
        () -> assertEquals(0, entity.getAttemptCount()),
        () -> assertEquals(REQUESTED_AT, entity.getRequestedAt()),
        () -> assertEquals(NOW, entity.getCreatedAt()),
        () -> assertEquals(NOW, entity.getUpdatedAt()));
  }

  @Test
  void shouldRoundTripRetryStateWithoutLosingFailureCursorStatisticsOrVersion() {
    ImportFailure failure =
        new ImportFailure(ImportFailureCategory.SOURCE_UNAVAILABLE, "source-unavailable");
    ImportRun run =
        ImportRun.reconstitute(
            IMPORT_RUN_ID,
            TENANT_ID,
            CONNECTOR_ID,
            ImportType.CUSTOMERS,
            ImportMode.INCREMENTAL,
            ImportStatus.RETRY_SCHEDULED,
            Optional.of(new ImportCursor("customer-105")),
            new ImportStatistics(5, 3, 1, 1),
            Optional.of(failure),
            2,
            REQUESTED_AT,
            Optional.of(STARTED_AT),
            Optional.empty(),
            Optional.of(NOW.plusSeconds(60)),
            0);
    ImportRunJpaEntity entity = mapper.toEntity(run, NOW);

    ImportRun mapped = mapper.toDomain(entity);

    assertAll(
        () -> assertEquals(run.id(), mapped.id()),
        () -> assertEquals(run.tenantId(), mapped.tenantId()),
        () -> assertEquals(run.connectorId(), mapped.connectorId()),
        () -> assertEquals(run.importType(), mapped.importType()),
        () -> assertEquals(run.mode(), mapped.mode()),
        () -> assertEquals(run.status(), mapped.status()),
        () -> assertEquals(run.committedCursor(), mapped.committedCursor()),
        () -> assertEquals(run.statistics(), mapped.statistics()),
        () -> assertEquals(run.failure(), mapped.failure()),
        () -> assertEquals(run.attemptCount(), mapped.attemptCount()),
        () -> assertEquals(run.nextRetryAt(), mapped.nextRetryAt()),
        () -> assertEquals(0, mapped.version()));
  }

  @Test
  void shouldUpdateOnlyMutableStateWhenTheExpectedVersionMatches() {
    ImportRunJpaEntity entity = mapper.toEntity(requestedRun(), NOW);
    ImportRun running = requestedRun();
    running.start(STARTED_AT);

    ImportRunJpaEntity updated = mapper.updateEntity(running, entity, NOW.plusSeconds(10));

    assertSame(entity, updated);
    assertAll(
        () -> assertEquals(IMPORT_RUN_ID.value(), updated.getId()),
        () -> assertEquals(TENANT_ID.value(), updated.getTenantId()),
        () -> assertEquals(CONNECTOR_ID.value(), updated.getConnectorId()),
        () -> assertEquals(ImportType.CUSTOMERS, updated.getImportType()),
        () -> assertEquals(ImportMode.INCREMENTAL, updated.getImportMode()),
        () -> assertEquals(REQUESTED_AT, updated.getRequestedAt()),
        () -> assertEquals(ImportStatus.RUNNING, updated.getStatus()),
        () -> assertEquals(1, updated.getAttemptCount()),
        () -> assertEquals(STARTED_AT, updated.getStartedAt()),
        () -> assertEquals(NOW.plusSeconds(10), updated.getUpdatedAt()));
  }

  @Test
  void shouldRejectAStaleDomainVersionBeforeOverwritingCurrentState() {
    ImportRunJpaEntity entity = mapper.toEntity(requestedRun(), NOW);
    ImportRun stale =
        ImportRun.reconstitute(
            IMPORT_RUN_ID,
            TENANT_ID,
            CONNECTOR_ID,
            ImportType.CUSTOMERS,
            ImportMode.INCREMENTAL,
            ImportStatus.REQUESTED,
            Optional.of(new ImportCursor("customer-100")),
            ImportStatistics.ZERO,
            Optional.empty(),
            0,
            REQUESTED_AT,
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            1);

    assertThrows(
        ConcurrentImportRunModificationException.class,
        () -> mapper.updateEntity(stale, entity, NOW));
  }

  private static ImportRun requestedRun() {
    return ImportRun.reconstitute(
        IMPORT_RUN_ID,
        TENANT_ID,
        CONNECTOR_ID,
        ImportType.CUSTOMERS,
        ImportMode.INCREMENTAL,
        ImportStatus.REQUESTED,
        Optional.of(new ImportCursor("customer-100")),
        ImportStatistics.ZERO,
        Optional.empty(),
        0,
        REQUESTED_AT,
        Optional.empty(),
        Optional.empty(),
        Optional.empty(),
        0);
  }
}
