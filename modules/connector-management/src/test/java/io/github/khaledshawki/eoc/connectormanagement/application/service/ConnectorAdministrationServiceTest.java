package io.github.khaledshawki.eoc.connectormanagement.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.khaledshawki.eoc.connectormanagement.application.exception.ConnectorAccessDeniedException;
import io.github.khaledshawki.eoc.connectormanagement.application.exception.ConnectorAlreadyActiveException;
import io.github.khaledshawki.eoc.connectormanagement.application.exception.ConnectorAlreadySuspendedException;
import io.github.khaledshawki.eoc.connectormanagement.application.exception.ConnectorNameAlreadyExistsException;
import io.github.khaledshawki.eoc.connectormanagement.application.exception.ConnectorNotActiveException;
import io.github.khaledshawki.eoc.connectormanagement.application.exception.ConnectorNotFoundException;
import io.github.khaledshawki.eoc.connectormanagement.application.exception.InvalidConnectorConfigurationException;
import io.github.khaledshawki.eoc.connectormanagement.application.model.authorization.ConnectorActor;
import io.github.khaledshawki.eoc.connectormanagement.application.model.authorization.ConnectorPermission;
import io.github.khaledshawki.eoc.connectormanagement.application.port.in.ActivateConnectorCommand;
import io.github.khaledshawki.eoc.connectormanagement.application.port.in.ConnectorResult;
import io.github.khaledshawki.eoc.connectormanagement.application.port.in.CreateConnectorCommand;
import io.github.khaledshawki.eoc.connectormanagement.application.port.in.GetConnectorQuery;
import io.github.khaledshawki.eoc.connectormanagement.application.port.in.ListConnectorsQuery;
import io.github.khaledshawki.eoc.connectormanagement.application.port.in.ListConnectorsResult;
import io.github.khaledshawki.eoc.connectormanagement.application.port.in.SuspendConnectorCommand;
import io.github.khaledshawki.eoc.connectormanagement.application.port.out.ConnectorAuthorizationPort;
import io.github.khaledshawki.eoc.connectormanagement.application.port.out.ConnectorRepository;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.Connector;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ConnectorEndpoint;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ConnectorId;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ConnectorName;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ConnectorStatus;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ConnectorTenantId;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ConnectorType;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.CredentialReference;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.SyncPolicy;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ConnectorAdministrationServiceTest {

  private static final UUID TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000010");
  private static final UUID OTHER_TENANT_ID =
      UUID.fromString("00000000-0000-0000-0000-000000000011");
  private static final UUID CREDENTIAL_REFERENCE =
      UUID.fromString("00000000-0000-0000-0000-000000000020");
  private static final ConnectorActor ACTOR =
      new ConnectorActor("https://identity.example.com/realms/eoc", "connector-administrator");

  private InMemoryConnectorRepository repository;
  private RecordingConnectorAuthorizationPort authorization;

  @BeforeEach
  void setUp() {
    repository = new InMemoryConnectorRepository();
    authorization = new RecordingConnectorAuthorizationPort();
  }

  @Test
  void shouldCreateDraftConnectorFromValidatedConfiguration() {
    CreateConnectorService service = new CreateConnectorService(repository, authorization);

    ConnectorResult result = service.create(createCommand(TENANT_ID, " Primary ERP "));

    assertEquals(TENANT_ID, result.tenantId().value());
    assertEquals("Primary ERP", result.name().value());
    assertEquals("mock-erp", result.type().value());
    assertEquals(ConnectorStatus.DRAFT, result.status());
    assertEquals(SyncPolicy.manual(), result.syncPolicy());
    assertEquals(result, ConnectorResult.from(repository.saved(result.connectorId())));
  }

  @Test
  void shouldRejectDuplicateNameBeforeSaving() {
    CreateConnectorService service = new CreateConnectorService(repository, authorization);
    service.create(createCommand(TENANT_ID, "Primary ERP"));

    assertThrows(
        ConnectorNameAlreadyExistsException.class,
        () -> service.create(createCommand(TENANT_ID, "Primary ERP")));
    assertEquals(1, repository.size());

    service.create(createCommand(OTHER_TENANT_ID, "Primary ERP"));
    assertEquals(2, repository.size());
  }

  @Test
  void shouldTranslateInvalidDomainInputIntoApplicationException() {
    CreateConnectorService service = new CreateConnectorService(repository, authorization);
    CreateConnectorCommand command =
        new CreateConnectorCommand(
            ACTOR,
            TENANT_ID,
            "Primary ERP",
            "mock-erp",
            "https://erp.example.com/api",
            CREDENTIAL_REFERENCE,
            SyncPolicy.Mode.MANUAL,
            Duration.ofMinutes(5));

    InvalidConnectorConfigurationException exception =
        assertThrows(InvalidConnectorConfigurationException.class, () -> service.create(command));

    assertEquals("Manual sync policy interval must be zero", exception.getMessage());
    assertEquals(0, repository.size());
  }

  @Test
  void shouldGetConnectorOnlyWithinRequestedTenant() {
    Connector connector = repository.save(connector(TENANT_ID, "Primary ERP"));
    GetConnectorService service = new GetConnectorService(repository, authorization);

    ConnectorResult result =
        service.get(new GetConnectorQuery(ACTOR, TENANT_ID, connector.id().value()));

    assertEquals(connector.id(), result.connectorId());
    assertThrows(
        ConnectorNotFoundException.class,
        () -> service.get(new GetConnectorQuery(ACTOR, OTHER_TENANT_ID, connector.id().value())));
  }

  @Test
  void shouldListOnlyConnectorsOwnedByRequestedTenant() {
    repository.save(connector(TENANT_ID, "Warehouse ERP"));
    repository.save(connector(TENANT_ID, "Primary ERP"));
    repository.save(connector(OTHER_TENANT_ID, "Other ERP"));
    ListConnectorsService service = new ListConnectorsService(repository, authorization);

    ListConnectorsResult result = service.list(new ListConnectorsQuery(ACTOR, TENANT_ID));

    assertEquals(List.of("Primary ERP", "Warehouse ERP"), names(result));
    assertThrows(
        UnsupportedOperationException.class,
        () -> result.connectors().add(result.connectors().getFirst()));
  }

  @Test
  void shouldActivateDraftAndSuspendedConnectors() {
    Connector connector = repository.save(connector(TENANT_ID, "Primary ERP"));
    ActivateConnectorService service = new ActivateConnectorService(repository, authorization);

    ConnectorResult active =
        service.activate(new ActivateConnectorCommand(ACTOR, TENANT_ID, connector.id().value()));
    assertEquals(ConnectorStatus.ACTIVE, active.status());

    connector.suspend();
    repository.save(connector);
    ConnectorResult reactivated =
        service.activate(new ActivateConnectorCommand(ACTOR, TENANT_ID, connector.id().value()));
    assertEquals(ConnectorStatus.ACTIVE, reactivated.status());
  }

  @Test
  void shouldRejectActivatingActiveConnector() {
    Connector connector = connector(TENANT_ID, "Primary ERP");
    connector.activate();
    repository.save(connector);

    assertThrows(
        ConnectorAlreadyActiveException.class,
        () ->
            new ActivateConnectorService(repository, authorization)
                .activate(new ActivateConnectorCommand(ACTOR, TENANT_ID, connector.id().value())));
  }

  @Test
  void shouldSuspendActiveConnector() {
    Connector connector = connector(TENANT_ID, "Primary ERP");
    connector.activate();
    repository.save(connector);

    ConnectorResult result =
        new SuspendConnectorService(repository, authorization)
            .suspend(new SuspendConnectorCommand(ACTOR, TENANT_ID, connector.id().value()));

    assertEquals(ConnectorStatus.SUSPENDED, result.status());
  }

  @Test
  void shouldRejectSuspendingDraftOrSuspendedConnector() {
    Connector draft = repository.save(connector(TENANT_ID, "Draft ERP"));
    SuspendConnectorService service = new SuspendConnectorService(repository, authorization);

    assertThrows(
        ConnectorNotActiveException.class,
        () -> service.suspend(new SuspendConnectorCommand(ACTOR, TENANT_ID, draft.id().value())));

    Connector suspended = connector(TENANT_ID, "Suspended ERP");
    suspended.activate();
    suspended.suspend();
    repository.save(suspended);

    assertThrows(
        ConnectorAlreadySuspendedException.class,
        () ->
            service.suspend(new SuspendConnectorCommand(ACTOR, TENANT_ID, suspended.id().value())));
  }

  @Test
  void shouldRejectUnknownConnectorForBothLifecycleOperations() {
    UUID connectorId = UUID.randomUUID();

    assertThrows(
        ConnectorNotFoundException.class,
        () ->
            new ActivateConnectorService(repository, authorization)
                .activate(new ActivateConnectorCommand(ACTOR, TENANT_ID, connectorId)));
    assertThrows(
        ConnectorNotFoundException.class,
        () ->
            new SuspendConnectorService(repository, authorization)
                .suspend(new SuspendConnectorCommand(ACTOR, TENANT_ID, connectorId)));
  }

  @Test
  void shouldRequestTheRequiredPermissionForEveryAdministrationUseCase() {
    Connector draft = repository.save(connector(TENANT_ID, "Primary ERP"));
    Connector active = connector(TENANT_ID, "Active ERP");
    active.activate();
    repository.save(active);

    new CreateConnectorService(repository, authorization)
        .create(createCommand(TENANT_ID, "Secondary ERP"));
    authorization.assertLastRequest(ACTOR, TENANT_ID, ConnectorPermission.ADMINISTER);

    new GetConnectorService(repository, authorization)
        .get(new GetConnectorQuery(ACTOR, TENANT_ID, draft.id().value()));
    authorization.assertLastRequest(ACTOR, TENANT_ID, ConnectorPermission.READ);

    new ListConnectorsService(repository, authorization)
        .list(new ListConnectorsQuery(ACTOR, TENANT_ID));
    authorization.assertLastRequest(ACTOR, TENANT_ID, ConnectorPermission.READ);

    new ActivateConnectorService(repository, authorization)
        .activate(new ActivateConnectorCommand(ACTOR, TENANT_ID, draft.id().value()));
    authorization.assertLastRequest(ACTOR, TENANT_ID, ConnectorPermission.ADMINISTER);

    new SuspendConnectorService(repository, authorization)
        .suspend(new SuspendConnectorCommand(ACTOR, TENANT_ID, active.id().value()));
    authorization.assertLastRequest(ACTOR, TENANT_ID, ConnectorPermission.ADMINISTER);
  }

  @Test
  void shouldDenyDirectUseCaseInvocationBeforeRepositoryAccess() {
    authorization.deny();

    assertThrows(
        ConnectorAccessDeniedException.class,
        () ->
            new CreateConnectorService(repository, authorization)
                .create(createCommand(TENANT_ID, "Primary ERP")));
    repository.assertNoInteractions();

    assertThrows(
        ConnectorAccessDeniedException.class,
        () ->
            new GetConnectorService(repository, authorization)
                .get(new GetConnectorQuery(ACTOR, TENANT_ID, UUID.randomUUID())));
    repository.assertNoInteractions();

    assertThrows(
        ConnectorAccessDeniedException.class,
        () ->
            new ListConnectorsService(repository, authorization)
                .list(new ListConnectorsQuery(ACTOR, TENANT_ID)));
    repository.assertNoInteractions();

    assertThrows(
        ConnectorAccessDeniedException.class,
        () ->
            new ActivateConnectorService(repository, authorization)
                .activate(new ActivateConnectorCommand(ACTOR, TENANT_ID, UUID.randomUUID())));
    repository.assertNoInteractions();

    assertThrows(
        ConnectorAccessDeniedException.class,
        () ->
            new SuspendConnectorService(repository, authorization)
                .suspend(new SuspendConnectorCommand(ACTOR, TENANT_ID, UUID.randomUUID())));
    repository.assertNoInteractions();
  }

  @Test
  void shouldRejectNullDependenciesAndInputs() {
    assertThrows(NullPointerException.class, () -> new CreateConnectorService(null, authorization));
    assertThrows(NullPointerException.class, () -> new CreateConnectorService(repository, null));
    assertThrows(NullPointerException.class, () -> new GetConnectorService(null, authorization));
    assertThrows(NullPointerException.class, () -> new GetConnectorService(repository, null));
    assertThrows(NullPointerException.class, () -> new ListConnectorsService(null, authorization));
    assertThrows(NullPointerException.class, () -> new ListConnectorsService(repository, null));
    assertThrows(
        NullPointerException.class, () -> new ActivateConnectorService(null, authorization));
    assertThrows(NullPointerException.class, () -> new ActivateConnectorService(repository, null));
    assertThrows(
        NullPointerException.class, () -> new SuspendConnectorService(null, authorization));
    assertThrows(NullPointerException.class, () -> new SuspendConnectorService(repository, null));

    assertThrows(
        NullPointerException.class,
        () -> new CreateConnectorService(repository, authorization).create(null));
    assertThrows(
        NullPointerException.class,
        () -> new GetConnectorService(repository, authorization).get(null));
    assertThrows(
        NullPointerException.class,
        () -> new ListConnectorsService(repository, authorization).list(null));
    assertThrows(
        NullPointerException.class,
        () -> new ActivateConnectorService(repository, authorization).activate(null));
    assertThrows(
        NullPointerException.class,
        () -> new SuspendConnectorService(repository, authorization).suspend(null));

    assertThrows(
        NullPointerException.class,
        () ->
            new CreateConnectorCommand(
                null,
                TENANT_ID,
                "Primary ERP",
                "mock-erp",
                "https://erp.example.com/api",
                CREDENTIAL_REFERENCE,
                SyncPolicy.Mode.MANUAL,
                Duration.ZERO));
    assertThrows(
        NullPointerException.class,
        () -> new GetConnectorQuery(null, TENANT_ID, UUID.randomUUID()));
    assertThrows(NullPointerException.class, () -> new ListConnectorsQuery(null, TENANT_ID));
    assertThrows(
        NullPointerException.class,
        () -> new ActivateConnectorCommand(null, TENANT_ID, UUID.randomUUID()));
    assertThrows(
        NullPointerException.class,
        () -> new SuspendConnectorCommand(null, TENANT_ID, UUID.randomUUID()));
  }

  private static CreateConnectorCommand createCommand(UUID tenantId, String name) {
    return new CreateConnectorCommand(
        ACTOR,
        tenantId,
        name,
        "mock-erp",
        "https://erp.example.com/api",
        CREDENTIAL_REFERENCE,
        SyncPolicy.Mode.MANUAL,
        Duration.ZERO);
  }

  private static Connector connector(UUID tenantId, String name) {
    return Connector.create(
        ConnectorTenantId.of(tenantId),
        ConnectorName.of(name),
        ConnectorType.of("mock-erp"),
        ConnectorEndpoint.of("https://erp.example.com/api"),
        CredentialReference.of(CREDENTIAL_REFERENCE),
        SyncPolicy.manual());
  }

  private static List<String> names(ListConnectorsResult result) {
    return result.connectors().stream().map(connector -> connector.name().value()).toList();
  }

  private static final class InMemoryConnectorRepository implements ConnectorRepository {

    private final Map<ConnectorId, Connector> connectors = new LinkedHashMap<>();
    private int interactions;

    @Override
    public Connector save(Connector connector) {
      interactions++;
      connectors.put(connector.id(), connector);
      return connector;
    }

    @Override
    public Optional<Connector> findById(ConnectorTenantId tenantId, ConnectorId connectorId) {
      interactions++;
      return Optional.ofNullable(connectors.get(connectorId))
          .filter(connector -> connector.tenantId().equals(tenantId));
    }

    @Override
    public List<Connector> findAllByTenantId(ConnectorTenantId tenantId) {
      interactions++;
      return connectors.values().stream()
          .filter(connector -> connector.tenantId().equals(tenantId))
          .toList();
    }

    @Override
    public boolean existsByTenantIdAndName(
        ConnectorTenantId tenantId, ConnectorName connectorName) {
      interactions++;
      return connectors.values().stream()
          .anyMatch(
              connector ->
                  connector.tenantId().equals(tenantId) && connector.name().equals(connectorName));
    }

    Connector saved(ConnectorId connectorId) {
      return connectors.get(connectorId);
    }

    int size() {
      return connectors.size();
    }

    void assertNoInteractions() {
      assertEquals(0, interactions);
    }
  }

  private static final class RecordingConnectorAuthorizationPort
      implements ConnectorAuthorizationPort {

    private boolean granted = true;
    private ConnectorActor actor;
    private ConnectorTenantId tenantId;
    private ConnectorPermission permission;

    @Override
    public boolean hasPermission(
        ConnectorActor actor, ConnectorTenantId tenantId, ConnectorPermission permission) {
      this.actor = actor;
      this.tenantId = tenantId;
      this.permission = permission;
      return granted;
    }

    void deny() {
      granted = false;
    }

    void assertLastRequest(
        ConnectorActor expectedActor,
        UUID expectedTenantId,
        ConnectorPermission expectedPermission) {
      assertEquals(expectedActor, actor);
      assertEquals(ConnectorTenantId.of(expectedTenantId), tenantId);
      assertEquals(expectedPermission, permission);
    }
  }
}
