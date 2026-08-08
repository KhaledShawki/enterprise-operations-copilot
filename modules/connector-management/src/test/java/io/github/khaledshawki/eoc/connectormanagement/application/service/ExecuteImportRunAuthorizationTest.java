package io.github.khaledshawki.eoc.connectormanagement.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.khaledshawki.eoc.connectormanagement.application.exception.ConnectorAccessDeniedException;
import io.github.khaledshawki.eoc.connectormanagement.application.model.authorization.ConnectorActor;
import io.github.khaledshawki.eoc.connectormanagement.application.model.authorization.ConnectorPermission;
import io.github.khaledshawki.eoc.connectormanagement.application.model.importing.ImportRetryPolicy;
import io.github.khaledshawki.eoc.connectormanagement.application.port.in.ExecuteImportRunCommand;
import io.github.khaledshawki.eoc.connectormanagement.application.port.in.FailImportRunCommand;
import io.github.khaledshawki.eoc.connectormanagement.application.port.in.ImportRunLifecycleUseCase;
import io.github.khaledshawki.eoc.connectormanagement.application.port.in.ImportRunReference;
import io.github.khaledshawki.eoc.connectormanagement.application.port.in.ImportRunResult;
import io.github.khaledshawki.eoc.connectormanagement.application.port.in.RecordAcceptedImportPageCommand;
import io.github.khaledshawki.eoc.connectormanagement.application.port.in.RequestImportRunCommand;
import io.github.khaledshawki.eoc.connectormanagement.application.port.in.ScheduleImportRetryCommand;
import io.github.khaledshawki.eoc.connectormanagement.application.port.out.ConnectorAuthorizationPort;
import io.github.khaledshawki.eoc.connectormanagement.application.port.out.ConnectorRepository;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.Connector;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ConnectorId;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ConnectorName;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ConnectorTenantId;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ExecuteImportRunAuthorizationTest {

  private static final ConnectorActor ACTOR =
      new ConnectorActor("https://identity.example.com/realms/eoc", "import-operator");
  private static final ConnectorTenantId TENANT_ID =
      ConnectorTenantId.of(UUID.fromString("00000000-0000-0000-0000-000000000001"));
  private static final UUID IMPORT_RUN_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");

  @Test
  void shouldDenyBeforeLifecycleRepositoryAndExternalBoundaryAccess() {
    RecordingDeniedAuthorization authorization = new RecordingDeniedAuthorization();
    ExecuteImportRunService service = service(authorization);

    assertThrows(
        ConnectorAccessDeniedException.class,
        () ->
            service.execute(
                new ExecuteImportRunCommand(ACTOR, TENANT_ID.value(), IMPORT_RUN_ID, 100)));

    authorization.assertRequested(ACTOR, TENANT_ID, ConnectorPermission.EXECUTE_IMPORT);
  }

  @Test
  void shouldRejectMissingActorBeforeExecution() {
    assertThrows(
        NullPointerException.class,
        () -> new ExecuteImportRunCommand(null, TENANT_ID.value(), IMPORT_RUN_ID, 100));
  }

  @Test
  void shouldRejectMissingAuthorizationDependency() {
    assertThrows(
        NullPointerException.class,
        () ->
            new ExecuteImportRunService(
                new FailingConnectorRepository(),
                null,
                new FailingImportRunLifecycle(),
                connectorType -> {
                  throw new AssertionError("Data source registry must not be called");
                },
                page -> {
                  throw new AssertionError("Downstream import must not be called");
                },
                page -> {
                  throw new AssertionError("Invoice downstream import must not be called");
                },
                page -> {
                  throw new AssertionError("Payment downstream import must not be called");
                },
                new ImportRetryPolicy(3, Duration.ofMinutes(1)),
                Clock.systemUTC()));
  }

  private static ExecuteImportRunService service(
      ConnectorAuthorizationPort connectorAuthorizationPort) {
    return new ExecuteImportRunService(
        new FailingConnectorRepository(),
        connectorAuthorizationPort,
        new FailingImportRunLifecycle(),
        connectorType -> {
          throw new AssertionError("Data source registry must not be called after denial");
        },
        page -> {
          throw new AssertionError("Downstream import must not be called after denial");
        },
        page -> {
          throw new AssertionError("Invoice downstream import must not be called after denial");
        },
        page -> {
          throw new AssertionError("Payment downstream import must not be called after denial");
        },
        new ImportRetryPolicy(3, Duration.ofMinutes(1)),
        Clock.systemUTC());
  }

  private static final class RecordingDeniedAuthorization implements ConnectorAuthorizationPort {

    private ConnectorActor actor;
    private ConnectorTenantId tenantId;
    private ConnectorPermission permission;

    @Override
    public boolean hasPermission(
        ConnectorActor actor, ConnectorTenantId tenantId, ConnectorPermission permission) {
      this.actor = actor;
      this.tenantId = tenantId;
      this.permission = permission;
      return false;
    }

    void assertRequested(
        ConnectorActor expectedActor,
        ConnectorTenantId expectedTenantId,
        ConnectorPermission expectedPermission) {
      assertEquals(expectedActor, actor);
      assertEquals(expectedTenantId, tenantId);
      assertEquals(expectedPermission, permission);
    }
  }

  private static final class FailingConnectorRepository implements ConnectorRepository {

    @Override
    public Connector save(Connector connector) {
      throw new AssertionError("Connector repository must not be called after denial");
    }

    @Override
    public Optional<Connector> findById(ConnectorTenantId tenantId, ConnectorId connectorId) {
      throw new AssertionError("Connector repository must not be called after denial");
    }

    @Override
    public List<Connector> findAllByTenantId(ConnectorTenantId tenantId) {
      throw new AssertionError("Connector repository must not be called after denial");
    }

    @Override
    public boolean existsByTenantIdAndName(
        ConnectorTenantId tenantId, ConnectorName connectorName) {
      throw new AssertionError("Connector repository must not be called after denial");
    }
  }

  private static final class FailingImportRunLifecycle implements ImportRunLifecycleUseCase {

    @Override
    public ImportRunResult request(RequestImportRunCommand command) {
      throw unexpectedInteraction();
    }

    @Override
    public ImportRunResult get(ImportRunReference reference) {
      throw unexpectedInteraction();
    }

    @Override
    public ImportRunResult start(ImportRunReference reference) {
      throw unexpectedInteraction();
    }

    @Override
    public ImportRunResult recordAcceptedPage(RecordAcceptedImportPageCommand command) {
      throw unexpectedInteraction();
    }

    @Override
    public ImportRunResult scheduleRetry(ScheduleImportRetryCommand command) {
      throw unexpectedInteraction();
    }

    @Override
    public ImportRunResult complete(ImportRunReference reference) {
      throw unexpectedInteraction();
    }

    @Override
    public ImportRunResult fail(FailImportRunCommand command) {
      throw unexpectedInteraction();
    }

    @Override
    public ImportRunResult requestCancellation(ImportRunReference reference) {
      throw unexpectedInteraction();
    }

    @Override
    public ImportRunResult confirmCancellation(ImportRunReference reference) {
      throw unexpectedInteraction();
    }

    private static AssertionError unexpectedInteraction() {
      return new AssertionError("Import run lifecycle must not be called after denial");
    }
  }
}
