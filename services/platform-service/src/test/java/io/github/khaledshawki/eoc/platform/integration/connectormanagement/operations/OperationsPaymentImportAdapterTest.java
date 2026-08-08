package io.github.khaledshawki.eoc.platform.integration.connectormanagement.operations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.khaledshawki.eoc.connectormanagement.application.model.datasource.SourceEntity;
import io.github.khaledshawki.eoc.connectormanagement.application.model.datasource.SourceIdentity;
import io.github.khaledshawki.eoc.connectormanagement.application.model.datasource.SourceModificationVersion;
import io.github.khaledshawki.eoc.connectormanagement.application.model.datasource.SourcePaymentRecord;
import io.github.khaledshawki.eoc.connectormanagement.application.model.datasource.SourceRecordMetadata;
import io.github.khaledshawki.eoc.connectormanagement.application.model.importing.DownstreamImportException;
import io.github.khaledshawki.eoc.connectormanagement.application.model.importing.PaymentImportOutcome;
import io.github.khaledshawki.eoc.connectormanagement.application.model.importing.PaymentImportPage;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ImportFailureCategory;
import io.github.khaledshawki.eoc.operations.application.exception.ConcurrentPaymentImportException;
import io.github.khaledshawki.eoc.operations.application.exception.ImportPageAcceptanceConflictException;
import io.github.khaledshawki.eoc.operations.application.exception.PaymentCustomerSourceMappingNotFoundException;
import io.github.khaledshawki.eoc.operations.application.port.in.ImportPaymentsCommand;
import io.github.khaledshawki.eoc.operations.application.port.in.PaymentImportResult;
import io.github.khaledshawki.eoc.operations.domain.model.ConflictingSourceRecordVersionException;
import io.github.khaledshawki.eoc.operations.domain.model.OperationsTenantId;
import io.github.khaledshawki.eoc.operations.domain.model.SourceRecordIdentity;
import io.github.khaledshawki.eoc.operations.domain.model.SourceSystemId;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Currency;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.TransientDataAccessException;

class OperationsPaymentImportAdapterTest {

  @Test
  void shouldTranslatePageIdentifiersAndReturnConnectorOutcome() {
    AtomicReference<ImportPaymentsCommand> captured = new AtomicReference<>();
    UUID acceptanceId = UUID.randomUUID();
    OperationsPaymentImportAdapter adapter =
        new OperationsPaymentImportAdapter(
            command -> {
              captured.set(command);
              return new PaymentImportResult(
                  command.pageAcceptanceId(), 4, 1, 1, 1, 1, Instant.parse("2026-08-06T08:30:00Z"));
            });
    PaymentImportPage page = page(acceptanceId, record("100.00", false));

    PaymentImportOutcome outcome = adapter.importPage(page);

    ImportPaymentsCommand command = captured.get();
    assertEquals(page.tenantId(), command.tenantId());
    assertEquals(page.sourceSystemId(), command.sourceSystemId());
    assertEquals(page.importRunId(), command.importBatchId());
    assertEquals(page.pageAcceptanceId(), command.pageAcceptanceId());
    assertEquals(1, command.records().size());
    assertEquals(acceptanceId, outcome.pageAcceptanceId());
    assertEquals(4, outcome.fetched());
    assertEquals(2, outcome.accepted());
    assertEquals(0, outcome.rejected());
    assertEquals(2, outcome.duplicates());
  }

  @Test
  void shouldMapConcurrentOperationsFailureToRetryableDownstreamFailure() {
    OperationsPaymentImportAdapter adapter =
        new OperationsPaymentImportAdapter(
            command -> {
              throw new ConcurrentPaymentImportException(
                  "concurrent", new IllegalStateException("collision"));
            });

    DownstreamImportException exception =
        assertThrows(
            DownstreamImportException.class,
            () -> adapter.importPage(page(record("100.00", false))));

    assertEquals(ImportFailureCategory.DOWNSTREAM_UNAVAILABLE, exception.failure().category());
    assertTrue(exception.failure().retryable());
    assertEquals("operations-concurrent-payment-import", exception.failure().diagnosticCode());
  }

  @Test
  void shouldMapTransientOperationsFailureToRetryableDownstreamFailure() {
    OperationsPaymentImportAdapter adapter =
        new OperationsPaymentImportAdapter(
            command -> {
              throw new TransientDataAccessException("temporary operations failure") {};
            });

    DownstreamImportException exception =
        assertThrows(
            DownstreamImportException.class,
            () -> adapter.importPage(page(record("100.00", false))));

    assertEquals(ImportFailureCategory.DOWNSTREAM_UNAVAILABLE, exception.failure().category());
    assertTrue(exception.failure().retryable());
    assertEquals("operations-temporarily-unavailable", exception.failure().diagnosticCode());
  }

  @Test
  void shouldMapAcceptanceAndVersionConflictsToSourceContractFailures() {
    UUID acceptanceId = UUID.randomUUID();
    OperationsPaymentImportAdapter acceptanceAdapter =
        new OperationsPaymentImportAdapter(
            command -> {
              throw new ImportPageAcceptanceConflictException(acceptanceId);
            });
    OperationsPaymentImportAdapter versionAdapter =
        new OperationsPaymentImportAdapter(
            command -> {
              throw new ConflictingSourceRecordVersionException(
                  SourceRecordIdentity.sourceRecordId("payment-1"), "conflicting evidence");
            });

    DownstreamImportException acceptanceFailure =
        assertThrows(
            DownstreamImportException.class,
            () -> acceptanceAdapter.importPage(page(acceptanceId, record("100.00", false))));
    DownstreamImportException versionFailure =
        assertThrows(
            DownstreamImportException.class,
            () -> versionAdapter.importPage(page(record("100.00", false))));

    assertEquals(
        "operations-payment-page-acceptance-conflict",
        acceptanceFailure.failure().diagnosticCode());
    assertEquals(
        "operations-payment-source-version-conflict", versionFailure.failure().diagnosticCode());
    assertEquals(
        ImportFailureCategory.SOURCE_CONTRACT_VIOLATION, acceptanceFailure.failure().category());
    assertEquals(
        ImportFailureCategory.SOURCE_CONTRACT_VIOLATION, versionFailure.failure().category());
  }

  @Test
  void shouldMapInvalidCustomerReferenceToPermanentSourceContractFailure() {
    OperationsPaymentImportAdapter adapter =
        new OperationsPaymentImportAdapter(
            command -> {
              throw new PaymentCustomerSourceMappingNotFoundException(
                  OperationsTenantId.of(command.tenantId()),
                  SourceSystemId.of(command.sourceSystemId()),
                  SourceRecordIdentity.sourceRecordId("missing-customer"));
            });

    DownstreamImportException exception =
        assertThrows(
            DownstreamImportException.class,
            () -> adapter.importPage(page(record("100.00", false))));

    assertEquals(ImportFailureCategory.SOURCE_CONTRACT_VIOLATION, exception.failure().category());
    assertEquals(
        "operations-payment-customer-reference-invalid", exception.failure().diagnosticCode());
  }

  @Test
  void shouldMapInvalidAmountsToSourceContractFailureBeforeCallingOperations() {
    OperationsPaymentImportAdapter adapter =
        new OperationsPaymentImportAdapter(
            command -> {
              throw new AssertionError("Operations use case must not receive an invalid record");
            });

    DownstreamImportException zeroFailure =
        assertThrows(
            DownstreamImportException.class, () -> adapter.importPage(page(record("0.00", false))));
    DownstreamImportException precisionFailure =
        assertThrows(
            DownstreamImportException.class,
            () -> adapter.importPage(page(record("1.001", false))));

    assertEquals(ImportFailureCategory.SOURCE_CONTRACT_VIOLATION, zeroFailure.failure().category());
    assertEquals("operations-invalid-payment-record", zeroFailure.failure().diagnosticCode());
    assertEquals(
        ImportFailureCategory.SOURCE_CONTRACT_VIOLATION, precisionFailure.failure().category());
  }

  @Test
  void shouldMapPersistenceCorruptionToUnexpectedFailure() {
    OperationsPaymentImportAdapter adapter =
        new OperationsPaymentImportAdapter(
            command -> {
              throw new DataIntegrityViolationException("broken operations state");
            });

    DownstreamImportException exception =
        assertThrows(
            DownstreamImportException.class,
            () -> adapter.importPage(page(record("100.00", false))));

    assertEquals(ImportFailureCategory.UNEXPECTED_FAILURE, exception.failure().category());
    assertEquals("operations-payment-state-corrupted", exception.failure().diagnosticCode());
  }

  private static PaymentImportPage page(SourcePaymentRecord record) {
    return page(UUID.randomUUID(), record);
  }

  private static PaymentImportPage page(UUID pageAcceptanceId, SourcePaymentRecord record) {
    return new PaymentImportPage(
        UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), pageAcceptanceId, List.of(record));
  }

  private static SourcePaymentRecord record(String amount, boolean reversed) {
    return new SourcePaymentRecord(
        new SourceRecordMetadata(
            SourceIdentity.sourceRecordId(SourceEntity.PAYMENT, "payment-1"),
            new SourceModificationVersion("payment-1-v1"),
            Optional.of(Instant.parse("2026-08-06T08:00:00Z"))),
        SourceIdentity.sourceRecordId(SourceEntity.CUSTOMER, "customer-1"),
        LocalDate.of(2026, 8, 1),
        Currency.getInstance("EUR"),
        new BigDecimal(amount),
        reversed);
  }
}
