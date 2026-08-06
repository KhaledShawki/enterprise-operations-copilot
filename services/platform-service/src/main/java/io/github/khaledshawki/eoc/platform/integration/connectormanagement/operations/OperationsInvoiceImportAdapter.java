package io.github.khaledshawki.eoc.platform.integration.connectormanagement.operations;

import io.github.khaledshawki.eoc.connectormanagement.application.model.importing.DownstreamImportException;
import io.github.khaledshawki.eoc.connectormanagement.application.model.importing.InvoiceImportOutcome;
import io.github.khaledshawki.eoc.connectormanagement.application.model.importing.InvoiceImportPage;
import io.github.khaledshawki.eoc.connectormanagement.application.port.out.InvoiceImportPort;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ImportFailure;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ImportFailureCategory;
import io.github.khaledshawki.eoc.operations.application.exception.BusinessPartnerSourceMappingCorruptedException;
import io.github.khaledshawki.eoc.operations.application.exception.ConcurrentInvoiceImportException;
import io.github.khaledshawki.eoc.operations.application.exception.ImportPageAcceptanceConflictException;
import io.github.khaledshawki.eoc.operations.application.exception.InvoiceCustomerRoleRequiredException;
import io.github.khaledshawki.eoc.operations.application.exception.InvoiceCustomerSourceMappingNotFoundException;
import io.github.khaledshawki.eoc.operations.application.exception.InvoiceSourceMappingCorruptedException;
import io.github.khaledshawki.eoc.operations.application.port.in.ImportInvoicesCommand;
import io.github.khaledshawki.eoc.operations.application.port.in.ImportInvoicesUseCase;
import io.github.khaledshawki.eoc.operations.application.port.in.InvoiceImportResult;
import io.github.khaledshawki.eoc.operations.domain.model.ConflictingSourceRecordReplayException;
import io.github.khaledshawki.eoc.operations.domain.model.ConflictingSourceRecordVersionException;
import java.util.Objects;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.stereotype.Component;

/** Explicit anti-corruption adapter from Connector Management Invoice pages into Operations. */
@Component
final class OperationsInvoiceImportAdapter implements InvoiceImportPort {

  private static final ImportFailure CONCURRENT_IMPORT_FAILURE =
      new ImportFailure(
          ImportFailureCategory.DOWNSTREAM_UNAVAILABLE, "operations-concurrent-invoice-import");
  private static final ImportFailure TRANSIENT_OPERATIONS_FAILURE =
      new ImportFailure(
          ImportFailureCategory.DOWNSTREAM_UNAVAILABLE, "operations-temporarily-unavailable");
  private static final ImportFailure ACCEPTANCE_CONFLICT_FAILURE =
      new ImportFailure(
          ImportFailureCategory.SOURCE_CONTRACT_VIOLATION,
          "operations-invoice-page-acceptance-conflict");
  private static final ImportFailure SOURCE_VERSION_CONFLICT_FAILURE =
      new ImportFailure(
          ImportFailureCategory.SOURCE_CONTRACT_VIOLATION,
          "operations-invoice-source-version-conflict");
  private static final ImportFailure CUSTOMER_REFERENCE_FAILURE =
      new ImportFailure(
          ImportFailureCategory.SOURCE_CONTRACT_VIOLATION,
          "operations-invoice-customer-reference-invalid");
  private static final ImportFailure INVALID_INVOICE_FAILURE =
      new ImportFailure(
          ImportFailureCategory.SOURCE_CONTRACT_VIOLATION, "operations-invalid-invoice-record");
  private static final ImportFailure CORRUPTED_OPERATIONS_STATE_FAILURE =
      new ImportFailure(
          ImportFailureCategory.UNEXPECTED_FAILURE, "operations-invoice-state-corrupted");

  private final ImportInvoicesUseCase importInvoicesUseCase;

  OperationsInvoiceImportAdapter(ImportInvoicesUseCase importInvoicesUseCase) {
    this.importInvoicesUseCase =
        Objects.requireNonNull(importInvoicesUseCase, "Operations Invoice use case cannot be null");
  }

  @Override
  public InvoiceImportOutcome importPage(InvoiceImportPage page) {
    Objects.requireNonNull(page, "Invoice import page cannot be null");
    InvoiceImportResult result;
    try {
      result =
          importInvoicesUseCase.importPage(
              new ImportInvoicesCommand(
                  page.tenantId(),
                  page.sourceSystemId(),
                  page.importRunId(),
                  page.pageAcceptanceId(),
                  page.records().stream()
                      .map(OperationsInvoiceRecordMapper::toOperationsRecord)
                      .toList()));
    } catch (ConcurrentInvoiceImportException exception) {
      throw new DownstreamImportException(CONCURRENT_IMPORT_FAILURE, exception);
    } catch (TransientDataAccessException exception) {
      throw new DownstreamImportException(TRANSIENT_OPERATIONS_FAILURE, exception);
    } catch (ImportPageAcceptanceConflictException exception) {
      throw new DownstreamImportException(ACCEPTANCE_CONFLICT_FAILURE, exception);
    } catch (ConflictingSourceRecordReplayException
        | ConflictingSourceRecordVersionException exception) {
      throw new DownstreamImportException(SOURCE_VERSION_CONFLICT_FAILURE, exception);
    } catch (InvoiceCustomerSourceMappingNotFoundException
        | InvoiceCustomerRoleRequiredException exception) {
      throw new DownstreamImportException(CUSTOMER_REFERENCE_FAILURE, exception);
    } catch (BusinessPartnerSourceMappingCorruptedException
        | InvoiceSourceMappingCorruptedException
        | DataIntegrityViolationException exception) {
      throw new DownstreamImportException(CORRUPTED_OPERATIONS_STATE_FAILURE, exception);
    } catch (IllegalArgumentException exception) {
      throw new DownstreamImportException(INVALID_INVOICE_FAILURE, exception);
    }

    return new InvoiceImportOutcome(
        result.pageAcceptanceId(),
        result.fetchedCount(),
        result.acceptedCount(),
        0,
        result.notAppliedCount());
  }
}
