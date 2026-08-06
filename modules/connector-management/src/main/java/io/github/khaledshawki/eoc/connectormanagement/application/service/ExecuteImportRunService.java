package io.github.khaledshawki.eoc.connectormanagement.application.service;

import io.github.khaledshawki.eoc.connectormanagement.application.exception.ConnectorAccessDeniedException;
import io.github.khaledshawki.eoc.connectormanagement.application.exception.ConnectorCannotStartImportException;
import io.github.khaledshawki.eoc.connectormanagement.application.exception.ConnectorNotFoundException;
import io.github.khaledshawki.eoc.connectormanagement.application.exception.ImportRunNotExecutableException;
import io.github.khaledshawki.eoc.connectormanagement.application.model.authorization.ConnectorPermission;
import io.github.khaledshawki.eoc.connectormanagement.application.model.datasource.BusinessDataSourceConfiguration;
import io.github.khaledshawki.eoc.connectormanagement.application.model.datasource.BusinessDataSourceException;
import io.github.khaledshawki.eoc.connectormanagement.application.model.datasource.BusinessDataSourceFailure;
import io.github.khaledshawki.eoc.connectormanagement.application.model.datasource.IncrementalCursor;
import io.github.khaledshawki.eoc.connectormanagement.application.model.datasource.SourceFetchRequest;
import io.github.khaledshawki.eoc.connectormanagement.application.model.datasource.SourcePage;
import io.github.khaledshawki.eoc.connectormanagement.application.model.datasource.SourcePageToken;
import io.github.khaledshawki.eoc.connectormanagement.application.model.importing.BusinessPartnerImportOutcome;
import io.github.khaledshawki.eoc.connectormanagement.application.model.importing.BusinessPartnerImportPage;
import io.github.khaledshawki.eoc.connectormanagement.application.model.importing.DownstreamImportException;
import io.github.khaledshawki.eoc.connectormanagement.application.model.importing.ImportRetryPolicy;
import io.github.khaledshawki.eoc.connectormanagement.application.model.importing.InvoiceImportOutcome;
import io.github.khaledshawki.eoc.connectormanagement.application.model.importing.InvoiceImportPage;
import io.github.khaledshawki.eoc.connectormanagement.application.port.in.ExecuteImportRunCommand;
import io.github.khaledshawki.eoc.connectormanagement.application.port.in.ExecuteImportRunUseCase;
import io.github.khaledshawki.eoc.connectormanagement.application.port.in.FailImportRunCommand;
import io.github.khaledshawki.eoc.connectormanagement.application.port.in.ImportRunLifecycleUseCase;
import io.github.khaledshawki.eoc.connectormanagement.application.port.in.ImportRunReference;
import io.github.khaledshawki.eoc.connectormanagement.application.port.in.ImportRunResult;
import io.github.khaledshawki.eoc.connectormanagement.application.port.in.RecordAcceptedImportPageCommand;
import io.github.khaledshawki.eoc.connectormanagement.application.port.in.ScheduleImportRetryCommand;
import io.github.khaledshawki.eoc.connectormanagement.application.port.out.BusinessDataSource;
import io.github.khaledshawki.eoc.connectormanagement.application.port.out.BusinessDataSourceRegistry;
import io.github.khaledshawki.eoc.connectormanagement.application.port.out.BusinessPartnerImportPort;
import io.github.khaledshawki.eoc.connectormanagement.application.port.out.ConnectorAuthorizationPort;
import io.github.khaledshawki.eoc.connectormanagement.application.port.out.ConnectorRepository;
import io.github.khaledshawki.eoc.connectormanagement.application.port.out.InvoiceImportPort;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.Connector;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ConnectorId;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ConnectorStatus;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ConnectorTenantId;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ImportFailure;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ImportFailureCategory;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ImportStatistics;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ImportStatus;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Coordinates one connector import attempt without holding a transaction across external I/O.
 * Source page tokens live only inside this method; committed cursors advance after downstream
 * acceptance and import-run progress are both durable.
 */
public final class ExecuteImportRunService implements ExecuteImportRunUseCase {

  private static final ImportFailure MISSING_DATA_SOURCE =
      new ImportFailure(
          ImportFailureCategory.SOURCE_CONTRACT_VIOLATION, "business-data-source-not-registered");

  private final ConnectorRepository connectorRepository;
  private final ConnectorAuthorizationPort connectorAuthorizationPort;
  private final ImportRunLifecycleUseCase importRunLifecycle;
  private final BusinessDataSourceRegistry dataSourceRegistry;
  private final BusinessPartnerImportPort businessPartnerImportPort;
  private final InvoiceImportPort invoiceImportPort;
  private final ImportRetryPolicy retryPolicy;
  private final Clock clock;

  public ExecuteImportRunService(
      ConnectorRepository connectorRepository,
      ConnectorAuthorizationPort connectorAuthorizationPort,
      ImportRunLifecycleUseCase importRunLifecycle,
      BusinessDataSourceRegistry dataSourceRegistry,
      BusinessPartnerImportPort businessPartnerImportPort,
      InvoiceImportPort invoiceImportPort,
      ImportRetryPolicy retryPolicy,
      Clock clock) {
    this.connectorRepository =
        Objects.requireNonNull(connectorRepository, "Connector repository cannot be null");
    this.connectorAuthorizationPort =
        Objects.requireNonNull(
            connectorAuthorizationPort, "Connector authorization port cannot be null");
    this.importRunLifecycle =
        Objects.requireNonNull(importRunLifecycle, "Import run lifecycle cannot be null");
    this.dataSourceRegistry =
        Objects.requireNonNull(dataSourceRegistry, "Business data source registry cannot be null");
    this.businessPartnerImportPort =
        Objects.requireNonNull(
            businessPartnerImportPort, "Business partner import port cannot be null");
    this.invoiceImportPort =
        Objects.requireNonNull(invoiceImportPort, "Invoice import port cannot be null");
    this.retryPolicy = Objects.requireNonNull(retryPolicy, "Import retry policy cannot be null");
    this.clock = Objects.requireNonNull(clock, "Clock cannot be null");
  }

  @Override
  public ImportRunResult execute(ExecuteImportRunCommand command) {
    Objects.requireNonNull(command, "Command cannot be null");

    ConnectorTenantId tenantId = ConnectorTenantId.of(command.tenantId());
    if (!connectorAuthorizationPort.hasPermission(
        command.actor(), tenantId, ConnectorPermission.EXECUTE_IMPORT)) {
      throw new ConnectorAccessDeniedException(tenantId, ConnectorPermission.EXECUTE_IMPORT);
    }

    ImportRunReference reference =
        new ImportRunReference(command.tenantId(), command.importRunId());
    ImportRunResult importRun = prepareRun(reference);
    if (importRun.status().terminal()) {
      return importRun;
    }

    Connector connector = loadActiveConnector(importRun);
    Optional<BusinessDataSource> resolvedDataSource =
        dataSourceRegistry.findByConnectorType(connector.type());
    if (resolvedDataSource.isEmpty()) {
      return importRunLifecycle.fail(
          new FailImportRunCommand(command.tenantId(), command.importRunId(), MISSING_DATA_SOURCE));
    }

    BusinessDataSource dataSource = resolvedDataSource.orElseThrow();
    BusinessDataSourceConfiguration configuration = BusinessDataSourceConfiguration.from(connector);
    return switch (importRun.importType()) {
      case CUSTOMERS ->
          executeCustomerPages(command, reference, importRun, dataSource, configuration);
      case INVOICES ->
          executeInvoicePages(command, reference, importRun, dataSource, configuration);
    };
  }

  private ImportRunResult executeCustomerPages(
      ExecuteImportRunCommand command,
      ImportRunReference reference,
      ImportRunResult importRun,
      BusinessDataSource dataSource,
      BusinessDataSourceConfiguration configuration) {
    return executePages(
        command,
        reference,
        importRun,
        configuration,
        dataSource::retrieveCustomers,
        (acceptanceId, records) -> {
          BusinessPartnerImportOutcome outcome =
              businessPartnerImportPort.importPage(
                  new BusinessPartnerImportPage(
                      command.tenantId(),
                      importRun.connectorId().value(),
                      command.importRunId(),
                      acceptanceId,
                      records));
          return AcceptedPageOutcome.from(outcome);
        });
  }

  private ImportRunResult executeInvoicePages(
      ExecuteImportRunCommand command,
      ImportRunReference reference,
      ImportRunResult importRun,
      BusinessDataSource dataSource,
      BusinessDataSourceConfiguration configuration) {
    return executePages(
        command,
        reference,
        importRun,
        configuration,
        dataSource::retrieveInvoices,
        (acceptanceId, records) -> {
          InvoiceImportOutcome outcome =
              invoiceImportPort.importPage(
                  new InvoiceImportPage(
                      command.tenantId(),
                      importRun.connectorId().value(),
                      command.importRunId(),
                      acceptanceId,
                      records));
          return AcceptedPageOutcome.from(outcome);
        });
  }

  private <T> ImportRunResult executePages(
      ExecuteImportRunCommand command,
      ImportRunReference reference,
      ImportRunResult initialRun,
      BusinessDataSourceConfiguration configuration,
      SourcePageRetriever<T> pageRetriever,
      DownstreamPageImporter<T> pageImporter) {
    ImportRunResult importRun = initialRun;
    Optional<SourcePageToken> pageToken = Optional.empty();

    while (true) {
      SourcePage<T> sourcePage;
      try {
        sourcePage =
            pageRetriever.retrieve(
                configuration, fetchRequest(command.pageSize(), pageToken, importRun));
      } catch (BusinessDataSourceException exception) {
        return transitionAfterFailure(importRun, mapSourceFailure(exception.failure()));
      }

      UUID acceptanceId = acceptanceId(importRun);
      AcceptedPageOutcome outcome;
      try {
        outcome = pageImporter.importPage(acceptanceId, sourcePage.records());
      } catch (DownstreamImportException exception) {
        return transitionAfterFailure(importRun, exception.failure());
      }
      if (!acceptanceId.equals(outcome.pageAcceptanceId())) {
        throw new IllegalStateException(
            "Downstream import returned a different page acceptance id");
      }

      importRun =
          importRunLifecycle.recordAcceptedPage(
              new RecordAcceptedImportPageCommand(
                  command.tenantId(),
                  command.importRunId(),
                  acceptanceId,
                  importRun.committedCursor().map(cursor -> cursor.value()),
                  sourcePage.candidateCursor().map(IncrementalCursor::value),
                  new ImportStatistics(
                      outcome.fetched(),
                      outcome.accepted(),
                      outcome.rejected(),
                      outcome.duplicates())));

      if (sourcePage.nextPageToken().isEmpty()) {
        return importRunLifecycle.complete(reference);
      }
      pageToken = sourcePage.nextPageToken();
    }
  }

  private ImportRunResult prepareRun(ImportRunReference reference) {
    ImportRunResult importRun = importRunLifecycle.get(reference);
    if (importRun.status().terminal()) {
      return importRun;
    }
    if (importRun.status() == ImportStatus.CANCELLING) {
      return importRunLifecycle.confirmCancellation(reference);
    }
    if (importRun.status() == ImportStatus.REQUESTED
        || importRun.status() == ImportStatus.RETRY_SCHEDULED) {
      return importRunLifecycle.start(reference);
    }
    if (importRun.status() != ImportStatus.RUNNING) {
      throw new ImportRunNotExecutableException(importRun.importRunId(), importRun.status());
    }
    return importRun;
  }

  private Connector loadActiveConnector(ImportRunResult importRun) {
    ConnectorTenantId tenantId = importRun.tenantId();
    ConnectorId connectorId = importRun.connectorId();
    Connector connector =
        connectorRepository
            .findById(tenantId, connectorId)
            .orElseThrow(() -> new ConnectorNotFoundException(tenantId, connectorId));
    if (connector.status() != ConnectorStatus.ACTIVE) {
      throw new ConnectorCannotStartImportException(tenantId, connectorId, connector.status());
    }
    return connector;
  }

  private ImportRunResult transitionAfterFailure(ImportRunResult importRun, ImportFailure failure) {
    if (failure.retryable() && retryPolicy.allowsAnotherAttempt(importRun.attemptCount())) {
      Instant now = clock.instant();
      return importRunLifecycle.scheduleRetry(
          new ScheduleImportRetryCommand(
              importRun.tenantId().value(),
              importRun.importRunId().value(),
              failure,
              retryPolicy.nextRetryAt(now)));
    }
    return importRunLifecycle.fail(
        new FailImportRunCommand(
            importRun.tenantId().value(), importRun.importRunId().value(), failure));
  }

  private static SourceFetchRequest fetchRequest(
      int pageSize, Optional<SourcePageToken> pageToken, ImportRunResult importRun) {
    if (pageToken.isPresent()) {
      return SourceFetchRequest.continueWith(pageSize, pageToken.orElseThrow());
    }
    return importRun
        .committedCursor()
        .map(cursor -> SourceFetchRequest.after(pageSize, new IncrementalCursor(cursor.value())))
        .orElseGet(() -> SourceFetchRequest.initial(pageSize));
  }

  private static UUID acceptanceId(ImportRunResult importRun) {
    String durablePagePosition =
        importRun.committedCursor().map(cursor -> cursor.value()).orElse("<start>");
    String name =
        "eoc:connector-import-page:v1:"
            + importRun.importRunId().value()
            + ":"
            + durablePagePosition;
    return UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8));
  }

  private static ImportFailure mapSourceFailure(BusinessDataSourceFailure failure) {
    ImportFailureCategory category =
        switch (failure.category()) {
          case AUTHENTICATION_FAILED -> ImportFailureCategory.AUTHENTICATION_FAILED;
          case AUTHORIZATION_FAILED -> ImportFailureCategory.AUTHORIZATION_FAILED;
          case SOURCE_UNAVAILABLE -> ImportFailureCategory.SOURCE_UNAVAILABLE;
          case TIMEOUT -> ImportFailureCategory.TIMEOUT;
          case RATE_LIMITED -> ImportFailureCategory.RATE_LIMITED;
          case INVALID_POSITION -> ImportFailureCategory.INVALID_CURSOR;
          case SOURCE_CONTRACT_VIOLATION -> ImportFailureCategory.SOURCE_CONTRACT_VIOLATION;
          case UNEXPECTED_SOURCE_FAILURE -> ImportFailureCategory.UNEXPECTED_FAILURE;
        };
    return new ImportFailure(category, failure.diagnosticCode());
  }

  @FunctionalInterface
  private interface SourcePageRetriever<T> {

    SourcePage<T> retrieve(
        BusinessDataSourceConfiguration configuration, SourceFetchRequest fetchRequest);
  }

  @FunctionalInterface
  private interface DownstreamPageImporter<T> {

    AcceptedPageOutcome importPage(UUID acceptanceId, List<T> records);
  }

  private record AcceptedPageOutcome(
      UUID pageAcceptanceId, long fetched, long accepted, long rejected, long duplicates) {

    private AcceptedPageOutcome {
      Objects.requireNonNull(pageAcceptanceId, "Page acceptance id cannot be null");
      if (fetched < 0 || accepted < 0 || rejected < 0 || duplicates < 0) {
        throw new IllegalArgumentException("Accepted page counts cannot be negative");
      }
      if (fetched != Math.addExact(Math.addExact(accepted, rejected), duplicates)) {
        throw new IllegalArgumentException(
            "Fetched records must equal accepted, rejected, and duplicate records");
      }
    }

    static AcceptedPageOutcome from(BusinessPartnerImportOutcome outcome) {
      Objects.requireNonNull(outcome, "Business partner import outcome cannot be null");
      return new AcceptedPageOutcome(
          outcome.pageAcceptanceId(),
          outcome.fetched(),
          outcome.accepted(),
          outcome.rejected(),
          outcome.duplicates());
    }

    static AcceptedPageOutcome from(InvoiceImportOutcome outcome) {
      Objects.requireNonNull(outcome, "Invoice import outcome cannot be null");
      return new AcceptedPageOutcome(
          outcome.pageAcceptanceId(),
          outcome.fetched(),
          outcome.accepted(),
          outcome.rejected(),
          outcome.duplicates());
    }
  }
}
