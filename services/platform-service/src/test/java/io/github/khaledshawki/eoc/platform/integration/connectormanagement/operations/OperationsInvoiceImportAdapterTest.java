package io.github.khaledshawki.eoc.platform.integration.connectormanagement.operations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.khaledshawki.eoc.connectormanagement.application.model.datasource.SourceEntity;
import io.github.khaledshawki.eoc.connectormanagement.application.model.datasource.SourceIdentity;
import io.github.khaledshawki.eoc.connectormanagement.application.model.datasource.SourceInvoiceRecord;
import io.github.khaledshawki.eoc.connectormanagement.application.model.datasource.SourceModificationVersion;
import io.github.khaledshawki.eoc.connectormanagement.application.model.datasource.SourceRecordMetadata;
import io.github.khaledshawki.eoc.connectormanagement.application.model.importing.DownstreamImportException;
import io.github.khaledshawki.eoc.connectormanagement.application.model.importing.InvoiceImportOutcome;
import io.github.khaledshawki.eoc.connectormanagement.application.model.importing.InvoiceImportPage;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ImportFailureCategory;
import io.github.khaledshawki.eoc.operations.application.exception.ConcurrentInvoiceImportException;
import io.github.khaledshawki.eoc.operations.application.exception.ImportPageAcceptanceConflictException;
import io.github.khaledshawki.eoc.operations.application.exception.InvoiceCustomerSourceMappingNotFoundException;
import io.github.khaledshawki.eoc.operations.application.port.in.ImportInvoicesCommand;
import io.github.khaledshawki.eoc.operations.application.port.in.InvoiceImportResult;
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

class OperationsInvoiceImportAdapterTest {

  @Test
  void shouldTranslatePageIdentifiersAndReturnConnectorOutcome() {
    AtomicReference<ImportInvoicesCommand> captured = new AtomicReference<>();
    UUID acceptedAtPage = UUID.randomUUID();
    OperationsInvoiceImportAdapter adapter =
        new OperationsInvoiceImportAdapter(
            command -> {
              captured.set(command);
              return new InvoiceImportResult(
                  command.pageAcceptanceId(), 4, 1, 1, 1, 1, Instant.parse("2026-08-06T08:30:00Z"));
            });
    InvoiceImportPage page = page(acceptedAtPage, record("OPEN"));

    InvoiceImportOutcome outcome = adapter.importPage(page);

    ImportInvoicesCommand command = captured.get();
    assertEquals(page.tenantId(), command.tenantId());
    assertEquals(page.sourceSystemId(), command.sourceSystemId());
    assertEquals(page.importRunId(), command.importBatchId());
    assertEquals(page.pageAcceptanceId(), command.pageAcceptanceId());
    assertEquals(1, command.records().size());
    assertEquals(acceptedAtPage, outcome.pageAcceptanceId());
    assertEquals(4, outcome.fetched());
    assertEquals(2, outcome.accepted());
    assertEquals(0, outcome.rejected());
    assertEquals(2, outcome.duplicates());
  }

  @Test
  void shouldMapConcurrentOperationsFailureToRetryableDownstreamFailure() {
    OperationsInvoiceImportAdapter adapter =
        new OperationsInvoiceImportAdapter(
            command -> {
              throw new ConcurrentInvoiceImportException(
                  "concurrent", new IllegalStateException("collision"));
            });

    DownstreamImportException exception =
        assertThrows(
            DownstreamImportException.class, () -> adapter.importPage(page(record("OPEN"))));

    assertEquals(ImportFailureCategory.DOWNSTREAM_UNAVAILABLE, exception.failure().category());
    assertTrue(exception.failure().retryable());
    assertEquals("operations-concurrent-invoice-import", exception.failure().diagnosticCode());
  }

  @Test
  void shouldMapTransientOperationsFailureToRetryableDownstreamFailure() {
    OperationsInvoiceImportAdapter adapter =
        new OperationsInvoiceImportAdapter(
            command -> {
              throw new TransientDataAccessException("temporary operations failure") {};
            });

    DownstreamImportException exception =
        assertThrows(
            DownstreamImportException.class, () -> adapter.importPage(page(record("OPEN"))));

    assertEquals(ImportFailureCategory.DOWNSTREAM_UNAVAILABLE, exception.failure().category());
    assertTrue(exception.failure().retryable());
    assertEquals("operations-temporarily-unavailable", exception.failure().diagnosticCode());
  }

  @Test
  void shouldMapAcceptanceAndVersionConflictsToSourceContractFailures() {
    UUID acceptanceId = UUID.randomUUID();
    OperationsInvoiceImportAdapter acceptanceAdapter =
        new OperationsInvoiceImportAdapter(
            command -> {
              throw new ImportPageAcceptanceConflictException(acceptanceId);
            });
    OperationsInvoiceImportAdapter versionAdapter =
        new OperationsInvoiceImportAdapter(
            command -> {
              throw new ConflictingSourceRecordVersionException(
                  SourceRecordIdentity.sourceRecordId("invoice-1"), "conflicting evidence");
            });

    DownstreamImportException acceptanceFailure =
        assertThrows(
            DownstreamImportException.class,
            () -> acceptanceAdapter.importPage(page(acceptanceId, record("OPEN"))));
    DownstreamImportException versionFailure =
        assertThrows(
            DownstreamImportException.class, () -> versionAdapter.importPage(page(record("OPEN"))));

    assertEquals(
        "operations-invoice-page-acceptance-conflict",
        acceptanceFailure.failure().diagnosticCode());
    assertEquals(
        "operations-invoice-source-version-conflict", versionFailure.failure().diagnosticCode());
    assertEquals(
        ImportFailureCategory.SOURCE_CONTRACT_VIOLATION, acceptanceFailure.failure().category());
    assertEquals(
        ImportFailureCategory.SOURCE_CONTRACT_VIOLATION, versionFailure.failure().category());
  }

  @Test
  void shouldMapInvalidCustomerReferenceToPermanentSourceContractFailure() {
    OperationsInvoiceImportAdapter adapter =
        new OperationsInvoiceImportAdapter(
            command -> {
              throw new InvoiceCustomerSourceMappingNotFoundException(
                  OperationsTenantId.of(command.tenantId()),
                  SourceSystemId.of(command.sourceSystemId()),
                  SourceRecordIdentity.sourceRecordId("missing-customer"));
            });

    DownstreamImportException exception =
        assertThrows(
            DownstreamImportException.class, () -> adapter.importPage(page(record("OPEN"))));

    assertEquals(ImportFailureCategory.SOURCE_CONTRACT_VIOLATION, exception.failure().category());
    assertEquals(
        "operations-invoice-customer-reference-invalid", exception.failure().diagnosticCode());
  }

  @Test
  void shouldMapUnsupportedStatusAndInvalidAmountsToSourceContractFailure() {
    OperationsInvoiceImportAdapter adapter =
        new OperationsInvoiceImportAdapter(
            command -> {
              throw new AssertionError("Operations use case must not receive an invalid record");
            });

    DownstreamImportException statusFailure =
        assertThrows(
            DownstreamImportException.class, () -> adapter.importPage(page(record("ARCHIVED"))));
    DownstreamImportException amountFailure =
        assertThrows(
            DownstreamImportException.class,
            () -> adapter.importPage(page(record("100.00", "101.00", "OPEN"))));

    assertEquals(
        ImportFailureCategory.SOURCE_CONTRACT_VIOLATION, statusFailure.failure().category());
    assertEquals("operations-invalid-invoice-record", statusFailure.failure().diagnosticCode());
    assertEquals(
        ImportFailureCategory.SOURCE_CONTRACT_VIOLATION, amountFailure.failure().category());
  }

  @Test
  void shouldMapPersistenceCorruptionToUnexpectedFailure() {
    OperationsInvoiceImportAdapter adapter =
        new OperationsInvoiceImportAdapter(
            command -> {
              throw new DataIntegrityViolationException("broken operations state");
            });

    DownstreamImportException exception =
        assertThrows(
            DownstreamImportException.class, () -> adapter.importPage(page(record("OPEN"))));

    assertEquals(ImportFailureCategory.UNEXPECTED_FAILURE, exception.failure().category());
    assertEquals("operations-invoice-state-corrupted", exception.failure().diagnosticCode());
  }

  private static InvoiceImportPage page(SourceInvoiceRecord record) {
    return page(UUID.randomUUID(), record);
  }

  private static InvoiceImportPage page(UUID pageAcceptanceId, SourceInvoiceRecord record) {
    return new InvoiceImportPage(
        UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), pageAcceptanceId, List.of(record));
  }

  private static SourceInvoiceRecord record(String sourceStatus) {
    return record("100.00", "40.00", sourceStatus);
  }

  private static SourceInvoiceRecord record(
      String totalAmount, String openAmount, String sourceStatus) {
    return new SourceInvoiceRecord(
        new SourceRecordMetadata(
            SourceIdentity.sourceRecordId(SourceEntity.INVOICE, "invoice-1"),
            new SourceModificationVersion("invoice-1-v1"),
            Optional.of(Instant.parse("2026-08-06T08:00:00Z"))),
        SourceIdentity.sourceRecordId(SourceEntity.CUSTOMER, "customer-1"),
        "INV-1",
        LocalDate.of(2026, 8, 1),
        LocalDate.of(2026, 8, 31),
        Currency.getInstance("EUR"),
        new BigDecimal(totalAmount),
        new BigDecimal(openAmount),
        sourceStatus);
  }
}
