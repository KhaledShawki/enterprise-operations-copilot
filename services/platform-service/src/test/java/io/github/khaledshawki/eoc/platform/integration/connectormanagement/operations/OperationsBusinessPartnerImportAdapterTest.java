package io.github.khaledshawki.eoc.platform.integration.connectormanagement.operations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.khaledshawki.eoc.connectormanagement.application.model.datasource.SourceCustomerRecord;
import io.github.khaledshawki.eoc.connectormanagement.application.model.datasource.SourceEntity;
import io.github.khaledshawki.eoc.connectormanagement.application.model.datasource.SourceIdentity;
import io.github.khaledshawki.eoc.connectormanagement.application.model.datasource.SourceModificationVersion;
import io.github.khaledshawki.eoc.connectormanagement.application.model.datasource.SourceRecordMetadata;
import io.github.khaledshawki.eoc.connectormanagement.application.model.importing.BusinessPartnerImportOutcome;
import io.github.khaledshawki.eoc.connectormanagement.application.model.importing.BusinessPartnerImportPage;
import io.github.khaledshawki.eoc.connectormanagement.application.model.importing.DownstreamImportException;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ImportFailureCategory;
import io.github.khaledshawki.eoc.operations.application.exception.ConcurrentBusinessPartnerImportException;
import io.github.khaledshawki.eoc.operations.application.port.in.BusinessPartnerImportResult;
import io.github.khaledshawki.eoc.operations.application.port.in.ImportBusinessPartnersCommand;
import io.github.khaledshawki.eoc.operations.application.port.in.ImportBusinessPartnersUseCase;
import io.github.khaledshawki.eoc.operations.domain.model.SourceRecordIdentity;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class OperationsBusinessPartnerImportAdapterTest {

  private static final UUID TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
  private static final UUID CONNECTOR_ID = UUID.fromString("00000000-0000-0000-0000-000000000010");
  private static final UUID IMPORT_RUN_ID = UUID.fromString("00000000-0000-0000-0000-000000000020");
  private static final UUID ACCEPTANCE_ID = UUID.fromString("00000000-0000-0000-0000-000000000030");
  private static final Instant ACCEPTED_AT = Instant.parse("2026-08-02T12:00:00Z");

  @Test
  void shouldTranslateConnectorCustomersAndDownstreamCounts() {
    AtomicReference<ImportBusinessPartnersCommand> capturedCommand = new AtomicReference<>();
    ImportBusinessPartnersUseCase useCase =
        command -> {
          capturedCommand.set(command);
          return new BusinessPartnerImportResult(ACCEPTANCE_ID, 1, 1, 0, 0, 0, ACCEPTED_AT);
        };
    OperationsBusinessPartnerImportAdapter adapter =
        new OperationsBusinessPartnerImportAdapter(useCase);

    BusinessPartnerImportOutcome outcome =
        adapter.importPage(
            new BusinessPartnerImportPage(
                TENANT_ID,
                CONNECTOR_ID,
                IMPORT_RUN_ID,
                ACCEPTANCE_ID,
                List.of(customer("customer-1", "v2", "C-1", "Acme GmbH"))));

    assertEquals(new BusinessPartnerImportOutcome(ACCEPTANCE_ID, 1, 1, 0, 0), outcome);
    ImportBusinessPartnersCommand command = capturedCommand.get();
    assertEquals(TENANT_ID, command.tenantId());
    assertEquals(CONNECTOR_ID, command.sourceSystemId());
    assertEquals(IMPORT_RUN_ID, command.importBatchId());
    assertEquals(ACCEPTANCE_ID, command.pageAcceptanceId());
    assertEquals(1, command.records().size());
    assertEquals(
        SourceRecordIdentity.sourceRecordId("customer-1"),
        command.records().getFirst().sourceIdentity());
    assertEquals("v2", command.records().getFirst().sourceVersion().value());
    assertEquals("C-1", command.records().getFirst().profile().partnerNumber());
    assertEquals("Acme GmbH", command.records().getFirst().profile().displayName());
  }

  @Test
  void shouldClassifyConcurrentOperationsImportAsRetryable() {
    ConcurrentBusinessPartnerImportException cause =
        new ConcurrentBusinessPartnerImportException("concurrent import", new RuntimeException());
    OperationsBusinessPartnerImportAdapter adapter =
        new OperationsBusinessPartnerImportAdapter(
            command -> {
              throw cause;
            });

    DownstreamImportException exception =
        assertThrows(
            DownstreamImportException.class,
            () ->
                adapter.importPage(
                    new BusinessPartnerImportPage(
                        TENANT_ID,
                        CONNECTOR_ID,
                        IMPORT_RUN_ID,
                        ACCEPTANCE_ID,
                        List.of(customer("customer-1", "v1", "C-1", "Acme")))));

    assertEquals(ImportFailureCategory.DOWNSTREAM_UNAVAILABLE, exception.failure().category());
    assertEquals("operations-concurrent-import", exception.failure().diagnosticCode());
    assertSame(cause, exception.getCause());
  }

  private static SourceCustomerRecord customer(
      String sourceId, String version, String number, String name) {
    return new SourceCustomerRecord(
        SourceRecordMetadata.withoutModificationTimestamp(
            SourceIdentity.sourceRecordId(SourceEntity.CUSTOMER, sourceId),
            new SourceModificationVersion(version)),
        number,
        name,
        Optional.empty());
  }
}
