package io.github.khaledshawki.eoc.platform.integration.connectormanagement.operations;

import io.github.khaledshawki.eoc.connectormanagement.application.model.datasource.SourceCustomerRecord;
import io.github.khaledshawki.eoc.connectormanagement.application.model.datasource.SourceIdentity;
import io.github.khaledshawki.eoc.connectormanagement.application.model.importing.BusinessPartnerImportOutcome;
import io.github.khaledshawki.eoc.connectormanagement.application.model.importing.BusinessPartnerImportPage;
import io.github.khaledshawki.eoc.connectormanagement.application.model.importing.DownstreamImportException;
import io.github.khaledshawki.eoc.connectormanagement.application.port.out.BusinessPartnerImportPort;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ImportFailure;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ImportFailureCategory;
import io.github.khaledshawki.eoc.operations.application.exception.ConcurrentBusinessPartnerImportException;
import io.github.khaledshawki.eoc.operations.application.exception.ImportPageAcceptanceConflictException;
import io.github.khaledshawki.eoc.operations.application.port.in.BusinessPartnerImportRecord;
import io.github.khaledshawki.eoc.operations.application.port.in.BusinessPartnerImportResult;
import io.github.khaledshawki.eoc.operations.application.port.in.ImportBusinessPartnersCommand;
import io.github.khaledshawki.eoc.operations.application.port.in.ImportBusinessPartnersUseCase;
import io.github.khaledshawki.eoc.operations.domain.model.BusinessPartnerProfile;
import io.github.khaledshawki.eoc.operations.domain.model.ConflictingSourceRecordVersionException;
import io.github.khaledshawki.eoc.operations.domain.model.SourceRecordIdentity;
import io.github.khaledshawki.eoc.operations.domain.model.SourceRecordVersion;
import java.util.Objects;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.stereotype.Component;

/** Explicit anti-corruption adapter between Connector Management and Operations. */
@Component
final class OperationsBusinessPartnerImportAdapter implements BusinessPartnerImportPort {

  private static final ImportFailure CONCURRENT_IMPORT_FAILURE =
      new ImportFailure(
          ImportFailureCategory.DOWNSTREAM_UNAVAILABLE, "operations-concurrent-import");
  private static final ImportFailure TRANSIENT_OPERATIONS_FAILURE =
      new ImportFailure(
          ImportFailureCategory.DOWNSTREAM_UNAVAILABLE, "operations-temporarily-unavailable");
  private static final ImportFailure ACCEPTANCE_CONFLICT_FAILURE =
      new ImportFailure(
          ImportFailureCategory.SOURCE_CONTRACT_VIOLATION, "operations-page-acceptance-conflict");
  private static final ImportFailure SOURCE_VERSION_CONFLICT_FAILURE =
      new ImportFailure(
          ImportFailureCategory.SOURCE_CONTRACT_VIOLATION, "operations-source-version-conflict");

  private final ImportBusinessPartnersUseCase importBusinessPartnersUseCase;

  OperationsBusinessPartnerImportAdapter(
      ImportBusinessPartnersUseCase importBusinessPartnersUseCase) {
    this.importBusinessPartnersUseCase =
        Objects.requireNonNull(
            importBusinessPartnersUseCase, "Operations import use case cannot be null");
  }

  @Override
  public BusinessPartnerImportOutcome importPage(BusinessPartnerImportPage page) {
    Objects.requireNonNull(page, "Business partner import page cannot be null");
    BusinessPartnerImportResult result;
    try {
      result =
          importBusinessPartnersUseCase.importPage(
              new ImportBusinessPartnersCommand(
                  page.tenantId(),
                  page.sourceSystemId(),
                  page.importRunId(),
                  page.pageAcceptanceId(),
                  page.records().stream()
                      .map(OperationsBusinessPartnerImportAdapter::toOperationsRecord)
                      .toList()));
    } catch (ConcurrentBusinessPartnerImportException exception) {
      throw new DownstreamImportException(CONCURRENT_IMPORT_FAILURE, exception);
    } catch (TransientDataAccessException exception) {
      throw new DownstreamImportException(TRANSIENT_OPERATIONS_FAILURE, exception);
    } catch (ImportPageAcceptanceConflictException exception) {
      throw new DownstreamImportException(ACCEPTANCE_CONFLICT_FAILURE, exception);
    } catch (ConflictingSourceRecordVersionException exception) {
      throw new DownstreamImportException(SOURCE_VERSION_CONFLICT_FAILURE, exception);
    }

    return new BusinessPartnerImportOutcome(
        result.pageAcceptanceId(),
        result.fetchedCount(),
        result.acceptedCount(),
        0,
        result.notAppliedCount());
  }

  private static BusinessPartnerImportRecord toOperationsRecord(SourceCustomerRecord record) {
    SourceIdentity identity = record.metadata().identity();
    SourceRecordIdentity operationsIdentity =
        switch (identity.kind()) {
          case SOURCE_RECORD_ID -> SourceRecordIdentity.sourceRecordId(identity.value());
          case CANONICAL_RECORD_HASH -> SourceRecordIdentity.canonicalRecordHash(identity.value());
        };
    return new BusinessPartnerImportRecord(
        operationsIdentity,
        new SourceRecordVersion(record.metadata().modificationVersion().value()),
        record.metadata().sourceModifiedAt(),
        new BusinessPartnerProfile(
            record.customerNumber(), record.displayName(), record.emailAddress()));
  }
}
