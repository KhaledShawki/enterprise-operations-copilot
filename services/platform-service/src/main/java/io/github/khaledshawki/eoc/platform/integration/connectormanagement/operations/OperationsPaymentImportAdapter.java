package io.github.khaledshawki.eoc.platform.integration.connectormanagement.operations;

import io.github.khaledshawki.eoc.connectormanagement.application.model.importing.DownstreamImportException;
import io.github.khaledshawki.eoc.connectormanagement.application.model.importing.PaymentImportOutcome;
import io.github.khaledshawki.eoc.connectormanagement.application.model.importing.PaymentImportPage;
import io.github.khaledshawki.eoc.connectormanagement.application.port.out.PaymentImportPort;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ImportFailure;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ImportFailureCategory;
import io.github.khaledshawki.eoc.operations.application.exception.BusinessPartnerSourceMappingCorruptedException;
import io.github.khaledshawki.eoc.operations.application.exception.ConcurrentPaymentImportException;
import io.github.khaledshawki.eoc.operations.application.exception.ImportPageAcceptanceConflictException;
import io.github.khaledshawki.eoc.operations.application.exception.PaymentCustomerRoleRequiredException;
import io.github.khaledshawki.eoc.operations.application.exception.PaymentCustomerSourceMappingNotFoundException;
import io.github.khaledshawki.eoc.operations.application.exception.PaymentSourceMappingCorruptedException;
import io.github.khaledshawki.eoc.operations.application.port.in.ImportPaymentsCommand;
import io.github.khaledshawki.eoc.operations.application.port.in.ImportPaymentsUseCase;
import io.github.khaledshawki.eoc.operations.application.port.in.PaymentImportResult;
import io.github.khaledshawki.eoc.operations.domain.model.ConflictingSourceRecordReplayException;
import io.github.khaledshawki.eoc.operations.domain.model.ConflictingSourceRecordVersionException;
import java.util.Objects;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.stereotype.Component;

/** Explicit anti-corruption adapter from Connector Management Payment pages into Operations. */
@Component
final class OperationsPaymentImportAdapter implements PaymentImportPort {

  private static final ImportFailure CONCURRENT_IMPORT_FAILURE =
      new ImportFailure(
          ImportFailureCategory.DOWNSTREAM_UNAVAILABLE, "operations-concurrent-payment-import");
  private static final ImportFailure TRANSIENT_OPERATIONS_FAILURE =
      new ImportFailure(
          ImportFailureCategory.DOWNSTREAM_UNAVAILABLE, "operations-temporarily-unavailable");
  private static final ImportFailure ACCEPTANCE_CONFLICT_FAILURE =
      new ImportFailure(
          ImportFailureCategory.SOURCE_CONTRACT_VIOLATION,
          "operations-payment-page-acceptance-conflict");
  private static final ImportFailure SOURCE_VERSION_CONFLICT_FAILURE =
      new ImportFailure(
          ImportFailureCategory.SOURCE_CONTRACT_VIOLATION,
          "operations-payment-source-version-conflict");
  private static final ImportFailure CUSTOMER_REFERENCE_FAILURE =
      new ImportFailure(
          ImportFailureCategory.SOURCE_CONTRACT_VIOLATION,
          "operations-payment-customer-reference-invalid");
  private static final ImportFailure INVALID_PAYMENT_FAILURE =
      new ImportFailure(
          ImportFailureCategory.SOURCE_CONTRACT_VIOLATION, "operations-invalid-payment-record");
  private static final ImportFailure CORRUPTED_OPERATIONS_STATE_FAILURE =
      new ImportFailure(
          ImportFailureCategory.UNEXPECTED_FAILURE, "operations-payment-state-corrupted");

  private final ImportPaymentsUseCase importPaymentsUseCase;

  OperationsPaymentImportAdapter(ImportPaymentsUseCase importPaymentsUseCase) {
    this.importPaymentsUseCase =
        Objects.requireNonNull(importPaymentsUseCase, "Operations Payment use case cannot be null");
  }

  @Override
  public PaymentImportOutcome importPage(PaymentImportPage page) {
    Objects.requireNonNull(page, "Payment import page cannot be null");
    PaymentImportResult result;
    try {
      result =
          importPaymentsUseCase.importPage(
              new ImportPaymentsCommand(
                  page.tenantId(),
                  page.sourceSystemId(),
                  page.importRunId(),
                  page.pageAcceptanceId(),
                  page.records().stream()
                      .map(OperationsPaymentRecordMapper::toOperationsRecord)
                      .toList()));
    } catch (ConcurrentPaymentImportException exception) {
      throw new DownstreamImportException(CONCURRENT_IMPORT_FAILURE, exception);
    } catch (TransientDataAccessException exception) {
      throw new DownstreamImportException(TRANSIENT_OPERATIONS_FAILURE, exception);
    } catch (ImportPageAcceptanceConflictException exception) {
      throw new DownstreamImportException(ACCEPTANCE_CONFLICT_FAILURE, exception);
    } catch (ConflictingSourceRecordReplayException
        | ConflictingSourceRecordVersionException exception) {
      throw new DownstreamImportException(SOURCE_VERSION_CONFLICT_FAILURE, exception);
    } catch (PaymentCustomerSourceMappingNotFoundException
        | PaymentCustomerRoleRequiredException exception) {
      throw new DownstreamImportException(CUSTOMER_REFERENCE_FAILURE, exception);
    } catch (BusinessPartnerSourceMappingCorruptedException
        | PaymentSourceMappingCorruptedException
        | DataIntegrityViolationException exception) {
      throw new DownstreamImportException(CORRUPTED_OPERATIONS_STATE_FAILURE, exception);
    } catch (IllegalArgumentException exception) {
      throw new DownstreamImportException(INVALID_PAYMENT_FAILURE, exception);
    }

    return new PaymentImportOutcome(
        result.pageAcceptanceId(),
        result.fetchedCount(),
        result.acceptedCount(),
        0,
        result.notAppliedCount());
  }
}
