package io.github.khaledshawki.eoc.connectormanagement.application.service;

import io.github.khaledshawki.eoc.connectormanagement.application.exception.ActiveImportRunAlreadyExistsException;
import io.github.khaledshawki.eoc.connectormanagement.application.exception.ConnectorCannotStartImportException;
import io.github.khaledshawki.eoc.connectormanagement.application.exception.ConnectorNotFoundException;
import io.github.khaledshawki.eoc.connectormanagement.application.exception.ImportPageAlreadyAcceptedException;
import io.github.khaledshawki.eoc.connectormanagement.application.exception.ImportRunNotFoundException;
import io.github.khaledshawki.eoc.connectormanagement.application.model.event.ConnectorIntegrationEvent;
import io.github.khaledshawki.eoc.connectormanagement.application.model.event.ImportRunIntegrationEventFactory;
import io.github.khaledshawki.eoc.connectormanagement.application.port.in.FailImportRunCommand;
import io.github.khaledshawki.eoc.connectormanagement.application.port.in.ImportRunLifecycleUseCase;
import io.github.khaledshawki.eoc.connectormanagement.application.port.in.ImportRunReference;
import io.github.khaledshawki.eoc.connectormanagement.application.port.in.ImportRunResult;
import io.github.khaledshawki.eoc.connectormanagement.application.port.in.RecordAcceptedImportPageCommand;
import io.github.khaledshawki.eoc.connectormanagement.application.port.in.RequestImportRunCommand;
import io.github.khaledshawki.eoc.connectormanagement.application.port.in.ScheduleImportRetryCommand;
import io.github.khaledshawki.eoc.connectormanagement.application.port.out.ConnectorEventIdGenerator;
import io.github.khaledshawki.eoc.connectormanagement.application.port.out.ConnectorRepository;
import io.github.khaledshawki.eoc.connectormanagement.application.port.out.ImportRunRepository;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.Connector;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ConnectorId;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ConnectorStatus;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ConnectorTenantId;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ImportCheckpoint;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ImportCursor;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ImportMode;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ImportPageAcceptanceId;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ImportRun;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ImportRunId;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

public final class ImportRunLifecycleService implements ImportRunLifecycleUseCase {

  private final ConnectorRepository connectorRepository;
  private final ImportRunRepository importRunRepository;
  private final ConnectorEventIdGenerator eventIdGenerator;
  private final Clock clock;

  public ImportRunLifecycleService(
      ConnectorRepository connectorRepository,
      ImportRunRepository importRunRepository,
      ConnectorEventIdGenerator eventIdGenerator,
      Clock clock) {
    this.connectorRepository =
        Objects.requireNonNull(connectorRepository, "Connector repository cannot be null");
    this.importRunRepository =
        Objects.requireNonNull(importRunRepository, "Import run repository cannot be null");
    this.eventIdGenerator =
        Objects.requireNonNull(eventIdGenerator, "Connector event id generator cannot be null");
    this.clock = Objects.requireNonNull(clock, "Clock cannot be null");
  }

  @Override
  public ImportRunResult request(RequestImportRunCommand command) {
    Objects.requireNonNull(command, "Command cannot be null");
    ConnectorTenantId tenantId = ConnectorTenantId.of(command.tenantId());
    ConnectorId connectorId = ConnectorId.of(command.connectorId());
    Connector connector =
        connectorRepository
            .findById(tenantId, connectorId)
            .orElseThrow(() -> new ConnectorNotFoundException(tenantId, connectorId));
    ensureConnectorActive(connector);
    if (importRunRepository.existsActive(tenantId, connectorId, command.importType())) {
      throw new ActiveImportRunAlreadyExistsException(tenantId, connectorId, command.importType());
    }

    Optional<ImportCursor> startingCursor =
        command.mode() == ImportMode.INCREMENTAL
            ? importRunRepository.findCheckpoint(tenantId, connectorId, command.importType())
            : Optional.empty();
    ImportRun importRun =
        ImportRun.request(
            tenantId,
            connectorId,
            command.importType(),
            command.mode(),
            startingCursor,
            clock.instant());
    return ImportRunResult.from(importRunRepository.save(importRun));
  }

  @Override
  public ImportRunResult get(ImportRunReference reference) {
    Objects.requireNonNull(reference, "Import run reference cannot be null");
    return ImportRunResult.from(load(reference));
  }

  @Override
  public ImportRunResult start(ImportRunReference reference) {
    ImportRun importRun = load(reference);
    Connector connector =
        connectorRepository
            .findById(importRun.tenantId(), importRun.connectorId())
            .orElseThrow(
                () ->
                    new ConnectorNotFoundException(importRun.tenantId(), importRun.connectorId()));
    ensureConnectorActive(connector);
    importRun.start(clock.instant());
    return save(importRun);
  }

  @Override
  public ImportRunResult recordAcceptedPage(RecordAcceptedImportPageCommand command) {
    Objects.requireNonNull(command, "Command cannot be null");
    ImportRunReference reference =
        new ImportRunReference(command.tenantId(), command.importRunId());
    ImportRunId importRunId = ImportRunId.of(command.importRunId());
    ImportPageAcceptanceId acceptanceId = ImportPageAcceptanceId.of(command.acceptanceId());
    if (importRunRepository.hasAcceptedPage(importRunId, acceptanceId)) {
      return get(reference);
    }

    ImportRun importRun = load(reference);
    Optional<ImportCursor> expectedCursor = command.expectedCursor().map(ImportCursor::new);
    Optional<ImportCursor> candidateCursor = command.candidateCursor().map(ImportCursor::new);
    importRun.recordAcceptedPage(expectedCursor, candidateCursor, command.statistics());
    Optional<ImportCheckpoint> checkpoint =
        candidateCursor.map(
            cursor ->
                new ImportCheckpoint(
                    importRun.tenantId(), importRun.connectorId(), importRun.importType(), cursor));

    try {
      return ImportRunResult.from(
          importRunRepository.saveAcceptedProgress(importRun, checkpoint, acceptanceId));
    } catch (ImportPageAlreadyAcceptedException exception) {
      return get(reference);
    }
  }

  @Override
  public ImportRunResult scheduleRetry(ScheduleImportRetryCommand command) {
    Objects.requireNonNull(command, "Command cannot be null");
    ImportRun importRun = load(new ImportRunReference(command.tenantId(), command.importRunId()));
    Instant occurredAt = clock.instant();
    importRun.scheduleRetry(command.failure(), command.nextRetryAt(), occurredAt);
    return saveWithEvent(
        importRun,
        ImportRunIntegrationEventFactory.retryScheduled(
            eventIdGenerator.generate(), importRun, occurredAt));
  }

  @Override
  public ImportRunResult complete(ImportRunReference reference) {
    ImportRun importRun = load(reference);
    Instant occurredAt = clock.instant();
    importRun.complete(occurredAt);
    return saveWithEvent(
        importRun,
        ImportRunIntegrationEventFactory.completed(
            eventIdGenerator.generate(), importRun, occurredAt));
  }

  @Override
  public ImportRunResult fail(FailImportRunCommand command) {
    Objects.requireNonNull(command, "Command cannot be null");
    ImportRun importRun = load(new ImportRunReference(command.tenantId(), command.importRunId()));
    Instant occurredAt = clock.instant();
    importRun.fail(command.failure(), occurredAt);
    return saveWithEvent(
        importRun,
        ImportRunIntegrationEventFactory.failed(
            eventIdGenerator.generate(), importRun, occurredAt));
  }

  @Override
  public ImportRunResult requestCancellation(ImportRunReference reference) {
    ImportRun importRun = load(reference);
    importRun.requestCancellation(clock.instant());
    return save(importRun);
  }

  @Override
  public ImportRunResult confirmCancellation(ImportRunReference reference) {
    ImportRun importRun = load(reference);
    importRun.confirmCancellation(clock.instant());
    return save(importRun);
  }

  private ImportRunResult save(ImportRun importRun) {
    return ImportRunResult.from(importRunRepository.save(importRun));
  }

  private ImportRunResult saveWithEvent(
      ImportRun importRun, ConnectorIntegrationEvent integrationEvent) {
    return ImportRunResult.from(importRunRepository.saveWithEvent(importRun, integrationEvent));
  }

  private ImportRun load(ImportRunReference reference) {
    Objects.requireNonNull(reference, "Import run reference cannot be null");
    ConnectorTenantId tenantId = ConnectorTenantId.of(reference.tenantId());
    ImportRunId importRunId = ImportRunId.of(reference.importRunId());
    return importRunRepository
        .findById(tenantId, importRunId)
        .orElseThrow(() -> new ImportRunNotFoundException(tenantId, importRunId));
  }

  private static void ensureConnectorActive(Connector connector) {
    if (connector.status() != ConnectorStatus.ACTIVE) {
      throw new ConnectorCannotStartImportException(
          connector.tenantId(), connector.id(), connector.status());
    }
  }
}
