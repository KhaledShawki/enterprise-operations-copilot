package io.github.khaledshawki.eoc.platform.integration.connectormanagement.observability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.khaledshawki.eoc.connectormanagement.application.model.authorization.ConnectorActor;
import io.github.khaledshawki.eoc.connectormanagement.application.port.in.ExecuteImportRunCommand;
import io.github.khaledshawki.eoc.connectormanagement.application.port.in.ExecuteImportRunUseCase;
import io.github.khaledshawki.eoc.connectormanagement.application.port.in.ImportRunResult;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ConnectorId;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ConnectorTenantId;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ImportMode;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ImportRunId;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ImportStatistics;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ImportStatus;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ImportType;
import io.github.khaledshawki.eoc.platform.observability.metrics.ConnectorImportMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MeteredExecuteImportRunUseCaseTest {

  private static final UUID TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
  private static final UUID RUN_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
  private static final ExecuteImportRunCommand COMMAND =
      new ExecuteImportRunCommand(new ConnectorActor("issuer", "subject"), TENANT_ID, RUN_ID, 100);

  @Test
  void recordsSuccessfulExecutionWithoutChangingResult() {
    SimpleMeterRegistry registry = new SimpleMeterRegistry();
    ImportRunResult result = result(ImportStatus.COMPLETED);
    ExecuteImportRunUseCase delegate = command -> result;
    MeteredExecuteImportRunUseCase useCase =
        new MeteredExecuteImportRunUseCase(delegate, new ConnectorImportMetrics(registry));

    assertSame(result, useCase.execute(COMMAND));
    assertEquals(
        1.0,
        registry
            .find("eoc.connector.import.execution")
            .tag("import_type", "invoices")
            .tag("outcome", "success")
            .counter()
            .count());
  }

  @Test
  void recordsUnexpectedFailureAndPreservesException() {
    SimpleMeterRegistry registry = new SimpleMeterRegistry();
    IllegalStateException failure = new IllegalStateException("source failure");
    ExecuteImportRunUseCase delegate =
        command -> {
          throw failure;
        };
    MeteredExecuteImportRunUseCase useCase =
        new MeteredExecuteImportRunUseCase(delegate, new ConnectorImportMetrics(registry));

    assertSame(failure, assertThrows(IllegalStateException.class, () -> useCase.execute(COMMAND)));
    assertEquals(
        1.0,
        registry
            .find("eoc.connector.import.execution")
            .tag("import_type", "unknown")
            .tag("outcome", "failure")
            .counter()
            .count());
  }

  private static ImportRunResult result(ImportStatus status) {
    Instant now = Instant.parse("2026-09-01T00:00:00Z");
    return new ImportRunResult(
        ImportRunId.of(RUN_ID),
        ConnectorTenantId.of(TENANT_ID),
        ConnectorId.of(UUID.fromString("00000000-0000-0000-0000-000000000003")),
        ImportType.INVOICES,
        ImportMode.FULL,
        status,
        Optional.empty(),
        ImportStatistics.ZERO,
        Optional.empty(),
        1,
        now,
        Optional.of(now),
        status.terminal() ? Optional.of(now) : Optional.empty(),
        Optional.empty(),
        1L);
  }
}
