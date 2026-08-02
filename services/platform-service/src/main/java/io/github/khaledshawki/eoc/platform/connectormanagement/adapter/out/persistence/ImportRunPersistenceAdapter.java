package io.github.khaledshawki.eoc.platform.connectormanagement.adapter.out.persistence;

import io.github.khaledshawki.eoc.connectormanagement.application.exception.ActiveImportRunAlreadyExistsException;
import io.github.khaledshawki.eoc.connectormanagement.application.exception.ConcurrentImportRunModificationException;
import io.github.khaledshawki.eoc.connectormanagement.application.exception.ImportPageAlreadyAcceptedException;
import io.github.khaledshawki.eoc.connectormanagement.application.port.out.ImportRunRepository;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ConnectorId;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ConnectorTenantId;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ImportCheckpoint;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ImportCursor;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ImportPageAcceptanceId;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ImportRun;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ImportRunId;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ImportStatus;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ImportType;
import io.github.khaledshawki.eoc.platform.persistence.PersistenceConstraintViolationDetector;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
class ImportRunPersistenceAdapter implements ImportRunRepository {

  private static final String ACTIVE_IMPORT_UNIQUE_INDEX = "uk_connector_import_runs_active";
  private static final EnumSet<ImportStatus> ACTIVE_STATUSES =
      EnumSet.of(
          ImportStatus.REQUESTED,
          ImportStatus.RUNNING,
          ImportStatus.RETRY_SCHEDULED,
          ImportStatus.CANCELLING);

  private final SpringDataImportRunRepository importRunRepository;
  private final SpringDataImportCheckpointRepository checkpointRepository;
  private final SpringDataImportPageAcceptanceRepository acceptanceRepository;
  private final ImportRunPersistenceMapper importRunPersistenceMapper;
  private final Clock clock;

  private final EntityManager entityManager;

  ImportRunPersistenceAdapter(
      SpringDataImportRunRepository importRunRepository,
      SpringDataImportCheckpointRepository checkpointRepository,
      SpringDataImportPageAcceptanceRepository acceptanceRepository,
      ImportRunPersistenceMapper importRunPersistenceMapper,
      Clock clock,
      EntityManager entityManager) {
    this.importRunRepository =
        Objects.requireNonNull(importRunRepository, "Import run repository cannot be null");
    this.checkpointRepository =
        Objects.requireNonNull(checkpointRepository, "Import checkpoint repository cannot be null");
    this.acceptanceRepository =
        Objects.requireNonNull(
            acceptanceRepository, "Import page acceptance repository cannot be null");
    this.importRunPersistenceMapper =
        Objects.requireNonNull(importRunPersistenceMapper, "Import run mapper cannot be null");
    this.clock = Objects.requireNonNull(clock, "Clock cannot be null");
    this.entityManager = Objects.requireNonNull(entityManager, "Entity manager cannot be null");
  }

  @Override
  @Transactional
  public ImportRun save(ImportRun importRun) {
    Objects.requireNonNull(importRun, "Import run cannot be null");
    try {
      ImportRunJpaEntity savedEntity = saveAndFlushEntity(importRun, clock.instant());
      entityManager.refresh(savedEntity);
      return importRunPersistenceMapper.toDomain(savedEntity);
    } catch (DataIntegrityViolationException exception) {
      if (PersistenceConstraintViolationDetector.hasConstraintName(
          exception, ACTIVE_IMPORT_UNIQUE_INDEX)) {
        throw new ActiveImportRunAlreadyExistsException(
            importRun.tenantId(), importRun.connectorId(), importRun.importType(), exception);
      }
      throw exception;
    } catch (ObjectOptimisticLockingFailureException exception) {
      throw new ConcurrentImportRunModificationException(importRun.id(), exception);
    }
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<ImportRun> findById(ConnectorTenantId tenantId, ImportRunId importRunId) {
    Objects.requireNonNull(tenantId, "Import run tenant id cannot be null");
    Objects.requireNonNull(importRunId, "Import run id cannot be null");
    return importRunRepository
        .findByIdAndTenantId(importRunId.value(), tenantId.value())
        .map(importRunPersistenceMapper::toDomain);
  }

  @Override
  @Transactional(readOnly = true)
  public boolean existsActive(
      ConnectorTenantId tenantId, ConnectorId connectorId, ImportType importType) {
    Objects.requireNonNull(tenantId, "Import run tenant id cannot be null");
    Objects.requireNonNull(connectorId, "Connector id cannot be null");
    Objects.requireNonNull(importType, "Import type cannot be null");
    return importRunRepository.existsByTenantIdAndConnectorIdAndImportTypeAndStatusIn(
        tenantId.value(), connectorId.value(), importType, ACTIVE_STATUSES);
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<ImportCursor> findCheckpoint(
      ConnectorTenantId tenantId, ConnectorId connectorId, ImportType importType) {
    Objects.requireNonNull(tenantId, "Import checkpoint tenant id cannot be null");
    Objects.requireNonNull(connectorId, "Import checkpoint connector id cannot be null");
    Objects.requireNonNull(importType, "Import checkpoint type cannot be null");
    ImportCheckpointJpaId id =
        new ImportCheckpointJpaId(tenantId.value(), connectorId.value(), importType.name());
    return checkpointRepository
        .findById(id)
        .map(ImportCheckpointJpaEntity::getCommittedCursor)
        .map(ImportCursor::new);
  }

  @Override
  @Transactional(readOnly = true)
  public boolean hasAcceptedPage(ImportRunId importRunId, ImportPageAcceptanceId acceptanceId) {
    Objects.requireNonNull(importRunId, "Import run id cannot be null");
    Objects.requireNonNull(acceptanceId, "Import page acceptance id cannot be null");
    return acceptanceRepository.existsById(
        new ImportPageAcceptanceJpaId(importRunId.value(), acceptanceId.value()));
  }

  @Override
  @Transactional
  public ImportRun saveAcceptedProgress(
      ImportRun importRun,
      Optional<ImportCheckpoint> checkpoint,
      ImportPageAcceptanceId acceptanceId) {
    Objects.requireNonNull(importRun, "Import run cannot be null");
    Objects.requireNonNull(checkpoint, "Import checkpoint cannot be null");
    Objects.requireNonNull(acceptanceId, "Import page acceptance id cannot be null");
    checkpoint.ifPresent(value -> ensureCheckpointBelongsToRun(value, importRun));
    Instant now = clock.instant();

    try {
      int inserted =
          acceptanceRepository.insertIfAbsent(importRun.id().value(), acceptanceId.value(), now);
      if (inserted == 0) {
        throw new ImportPageAlreadyAcceptedException(importRun.id(), acceptanceId);
      }
      ImportRunJpaEntity savedEntity = saveAndFlushEntity(importRun, now);
      checkpoint.ifPresent(value -> saveCheckpoint(value, importRun.id(), now));
      return importRunPersistenceMapper.toDomain(savedEntity);
    } catch (ObjectOptimisticLockingFailureException exception) {
      throw new ConcurrentImportRunModificationException(importRun.id(), exception);
    }
  }

  private ImportRunJpaEntity saveAndFlushEntity(ImportRun importRun, Instant now) {
    ImportRunJpaEntity entity =
        importRunRepository
            .findByIdAndTenantId(importRun.id().value(), importRun.tenantId().value())
            .map(existing -> importRunPersistenceMapper.updateEntity(importRun, existing, now))
            .orElseGet(() -> importRunPersistenceMapper.toEntity(importRun, now));

    return importRunRepository.saveAndFlush(entity);
  }

  private void saveCheckpoint(ImportCheckpoint checkpoint, ImportRunId importRunId, Instant now) {
    ImportCheckpointJpaId id =
        new ImportCheckpointJpaId(
            checkpoint.tenantId().value(),
            checkpoint.connectorId().value(),
            checkpoint.importType().name());
    ImportCheckpointJpaEntity entity =
        checkpointRepository
            .findById(id)
            .map(
                existing -> {
                  existing.update(checkpoint.cursor().value(), importRunId.value(), now);
                  return existing;
                })
            .orElseGet(
                () ->
                    new ImportCheckpointJpaEntity(
                        checkpoint.tenantId().value(),
                        checkpoint.connectorId().value(),
                        checkpoint.importType().name(),
                        checkpoint.cursor().value(),
                        importRunId.value(),
                        now));
    checkpointRepository.saveAndFlush(entity);
  }

  private void ensureCheckpointBelongsToRun(ImportCheckpoint checkpoint, ImportRun importRun) {
    if (!checkpoint.tenantId().equals(importRun.tenantId())
        || !checkpoint.connectorId().equals(importRun.connectorId())
        || checkpoint.importType() != importRun.importType()
        || !importRun.committedCursor().equals(Optional.of(checkpoint.cursor()))) {
      throw new IllegalArgumentException("Import checkpoint does not match the import run");
    }
  }
}
